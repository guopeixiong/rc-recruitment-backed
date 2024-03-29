package com.ruanchuang.service.impl;

import cn.dev33.satoken.secure.SaSecureUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ruanchuang.constant.CacheConstants;
import com.ruanchuang.constant.Constants;
import com.ruanchuang.domain.SysFile;
import com.ruanchuang.domain.SysLog;
import com.ruanchuang.domain.SysUser;
import com.ruanchuang.domain.dto.*;
import com.ruanchuang.enums.BusinessStatus;
import com.ruanchuang.enums.UserType;
import com.ruanchuang.exception.ServiceException;
import com.ruanchuang.exception.SystemException;
import com.ruanchuang.mapper.SysUserMapper;
import com.ruanchuang.service.SysFileService;
import com.ruanchuang.service.SysLogService;
import com.ruanchuang.service.SysUserService;
import com.ruanchuang.utils.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 用户信息表 服务实现类
 * </p>
 *
 * @author guopeixiong
 * @since 2023-07-30
 */
@Slf4j
@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    @Resource(name = "systemThreadPool")
    private ThreadPoolTaskExecutor systemThreadPool;

    @Resource(name = "businessThreadPool")
    private ThreadPoolTaskExecutor businessThreadPool;

    @Autowired
    private SysLogService sysLogService;

    @Autowired
    private SysFileService sysFileService;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private EmailUtils emailUtils;

    @Value("${file.store-address}")
    private String rootPath;

    @Value("${user.max-password-error-times}")
    private Integer pwdErrTimes;

    @Value("${user.password-error-lock-time}")
    private Integer pwdErrLockTime;

    /**
     * 手机号或者学号密码登录方式
     *
     * @param loginDto
     * @param request
     * @return
     */
    @Override
    public String loginByPhoneAndPassword(LoginDto loginDto, HttpServletRequest request) {
        if (redisTemplate.hasKey(CacheConstants.USER_FORBIDDEN + loginDto.getStuNum())) {
            throw new ServiceException("您的账号密码错误次数超过" + pwdErrTimes + "次, 请" + pwdErrLockTime + "分钟后再试");
        }
        loginDto.setPassword(RSAUtils.decryptByRsa(loginDto.getPassword()));
        SysUser user = this.baseMapper.selectOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getStuNum, loginDto.getStuNum()));
        if (user == null) {
            user = this.baseMapper.selectOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getPhone, loginDto.getStuNum()));
        }
        if (user == null) {
            throw new ServiceException("账号不存在");
        }
        checkUserStatus(user);
        String password = SaSecureUtil.md5BySalt(loginDto.getPassword(), user.getSalt());
        if (!password.equals(user.getPassword())) {
            saveLoginLog(loginDto, null, request, false);
            Long count = redisTemplate.opsForValue().increment(CacheConstants.PWD_ERR_CNT_KEY + loginDto.getStuNum());
            if (count >= pwdErrTimes) {
                redisTemplate.opsForValue().set(CacheConstants.USER_FORBIDDEN + loginDto.getStuNum(), "lock");
                redisTemplate.expire(CacheConstants.USER_FORBIDDEN + loginDto.getStuNum(), pwdErrLockTime, TimeUnit.MINUTES);
                redisTemplate.delete(CacheConstants.PWD_ERR_CNT_KEY + loginDto.getStuNum());
                throw new ServiceException("您的账号密码错误次数超过" + pwdErrTimes + "次, 请" + pwdErrLockTime + "分钟后再试");
            }
            redisTemplate.expire(CacheConstants.PWD_ERR_CNT_KEY + loginDto.getStuNum(), pwdErrLockTime, TimeUnit.MINUTES);
            throw new ServiceException("密码错误");
        }
        String token = LoginUtils.login(user);
        final Long userId = user.getId();
        this.saveLoginLog(loginDto, userId, request, true);
        return token;
    }

    /**
     * 邮箱验证码登录
     *
     * @param loginDto
     * @param request
     * @return
     */
    @Override
    public String loginByEmailCode(LoginDto loginDto, HttpServletRequest request) {
        String code = (String) redisTemplate.opsForValue().get(CacheConstants.CAPTCHA_CODE_KEY_LOGIN + loginDto.getEmail());
        if (!loginDto.getCode().equals(code)) {
            throw new ServiceException("验证码无效");
        }
        redisTemplate.delete(CacheConstants.CAPTCHA_CODE_KEY_LOGIN + loginDto.getEmail());
        SysUser user = this.baseMapper.selectOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getEmail, loginDto.getEmail()));
        checkUserStatus(user);
        String token = LoginUtils.login(user);
        this.saveLoginLog(loginDto, user.getId(), request, true);
        return token;
    }

    /**
     * 用户注册
     * @param registerDto
     */
    @Override
    public void userRegister(RegisterDto registerDto) {
        String code = (String) redisTemplate.opsForValue().get(CacheConstants.CAPTCHA_CODE_KEY_REGISTER + registerDto.getEmail());
        if (!registerDto.getCode().equals(code)) {
            throw new ServiceException("验证码无效");
        }
        redisTemplate.delete(CacheConstants.CAPTCHA_CODE_KEY_REGISTER + registerDto.getEmail());
        Long count = this.lambdaQuery().eq(SysUser::getStuNum, registerDto.getStuNum()).count();
        if (count.longValue() > 0) {
            throw new ServiceException("该学号已经被注册");
        }
        count = this.lambdaQuery().eq(SysUser::getEmail, registerDto.getEmail()).count();
        if (count.longValue() > 0) {
            throw new ServiceException("该邮箱已被注册");
        }
        registerDto.setPassword(RSAUtils.decryptByRsa(registerDto.getPassword()));
        String salt = RandomUtil.randomString(6);
        String password = SaSecureUtil.md5BySalt(registerDto.getPassword(), salt);
        SysUser user = new SysUser();
        user.setEmail(registerDto.getEmail())
                .setStuNum(registerDto.getStuNum())
                .setType(UserType.AVERAGE_USER.getValue())
                .setStatus(0)
                .setPassword(password)
                .setSalt(salt);
        this.save(user);
    }

    /**
     * 用户修改个人信息
     * @param user
     * @return
     */
    @Override
    public boolean updateUserInfo(UpdateUserInfoDto user) {
        if (StringUtils.isNotBlank(user.getPhone())) {
            if (!user.getPhone().matches("1[3-9]\\d{9}")) {
                throw new ServiceException("手机号格式错误");
            }
        }
        SysUser loginUser = LoginUtils.getLoginUser();
        SysUser sysUser = new SysUser();
        sysUser.setId(loginUser.getId());
        BeanUtil.copyProperties(user, sysUser);
        boolean result = this.updateById(sysUser);
        if (result) {
            LoginUtils.updateUserInfo(this.getById(sysUser.getId()));
        }
        return result;
    }

    /**
     * 用户重置密码
     * @param forgetPasswordDto
     */
    @Override
    public void resetPwd(ForgetPasswordDto forgetPasswordDto) {
        String code = (String) redisTemplate.opsForValue().get(CacheConstants.CAPTCHA_CODE_KEY_FORGET_PWD + forgetPasswordDto.getEmail());
        if (code == null || !code.equals(forgetPasswordDto.getCode())) {
            throw new ServiceException("验证码无效");
        }
        redisTemplate.delete(CacheConstants.CAPTCHA_CODE_KEY_FORGET_PWD + forgetPasswordDto.getEmail());
        SysUser user = getUserByEmail(forgetPasswordDto.getEmail());
        String newPassword = RSAUtils.decryptByRsa(forgetPasswordDto.getPassword());
        String newSalt = RandomUtil.randomString(6);
        newPassword = SaSecureUtil.md5BySalt(newPassword, newSalt);
        SysUser userInfo = new SysUser()
                .setId(user.getId())
                .setSalt(newSalt)
                .setPassword(newPassword);
        this.updateById(userInfo);
    }

    /**
     * 用户修改密码
     * @param updatePwdDto
     */
    @Override
    public void updatePwd(UpdatePwdDto updatePwdDto) {
        String code = (String) redisTemplate.opsForValue().get(CacheConstants.CAPTCHA_CODE_KEY_UPDATE_PWD + updatePwdDto.getEmail());
        if (code == null || !code.equals(updatePwdDto.getCode())) {
            throw new ServiceException("验证码无效");
        }
        redisTemplate.delete(CacheConstants.CAPTCHA_CODE_KEY_UPDATE_PWD + updatePwdDto.getEmail());
        SysUser user = getUserByEmail(updatePwdDto.getEmail());
        String oldPassword = RSAUtils.decryptByRsa(updatePwdDto.getOldPassword());
        String newPassword = RSAUtils.decryptByRsa(updatePwdDto.getNewPassword());
        if (!SaSecureUtil.md5BySalt(oldPassword, user.getSalt()).equals(user.getPassword())) {
            throw new ServiceException("原密码错误");
        }
        if (oldPassword.equals(newPassword)) {
            throw new ServiceException("新密码与旧密码一致");
        }
        String salt = RandomUtil.randomString(6);
        newPassword = SaSecureUtil.md5BySalt(newPassword, salt);
        SysUser userInfo = new SysUser()
                .setId(user.getId())
                .setSalt(salt)
                .setPassword(newPassword);
        this.updateById(userInfo);
    }

    /**
     * 用户上传头像
     * @param file
     */
    @Override
    public String uploadAvatar(MultipartFile file) {
        File path = new File(rootPath + File.separator + "userAvatar");
        if (!path.exists()) {
            path.mkdirs();
        }
        SysUser user = LoginUtils.getLoginUser();
        String fileName = user.getId().longValue() + "_" + System.currentTimeMillis() + "_" + file.getOriginalFilename();
        File targetFile = new File(path.getPath() + File.separator + fileName);
        try {
            FileCopyUtils.copy(file.getBytes(), targetFile);
        } catch (IOException e) {
            log.error("用户上传头像异常, 异常信息: '{}'", e.getMessage());
            throw new  SystemException("用户上传头像异常");
        }
        String imgUrl = "/userAvatar/" + fileName;
        String oldAvatar = user.getAvatar();
        businessThreadPool.execute(() -> {
            SysFile sysFile = new SysFile()
                    .setPath(targetFile.getPath())
                    .setLinkPath(imgUrl)
                    .setOldFileName(file.getOriginalFilename())
                    .setRemark("用户头像");
            sysFileService.save(sysFile);
            if (StringUtils.isBlank(user.getAvatar())) {
                return;
            }
            SysFile oldFile = sysFileService.getBaseMapper().selectOne(Wrappers.<SysFile>lambdaQuery().eq(SysFile::getLinkPath, oldAvatar));
            if (oldFile == null) {
                return;
            }
            new File(oldFile.getPath()).delete();
            sysFileService.removeById(oldFile.getId());
        });
        user.setAvatar(imgUrl);
        this.updateById(user);
        LoginUtils.updateUserInfo(user);
        return imgUrl;
    }

    /**
     * 管理员登录
     * @param loginDto
     * @param request
     * @return
     */
    @Override
    public String adminLogin(LoginDto loginDto, HttpServletRequest request) {
        if (redisTemplate.hasKey(CacheConstants.USER_FORBIDDEN + loginDto.getStuNum())) {
            throw new ServiceException("您的账号密码错误次数超过" + pwdErrTimes + "次, 请" + pwdErrLockTime + "分钟后再试");
        }
        loginDto.setPassword(RSAUtils.decryptByRsa(loginDto.getPassword()));
        SysUser user = this.baseMapper.selectOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getStuNum, loginDto.getStuNum()));
        if (user == null) {
            user = this.baseMapper.selectOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getPhone, loginDto.getStuNum()));
        }
        if (user == null) {
            throw new ServiceException("账号不存在");
        }
        checkUserStatus(user);
        if (!user.getType().equals(UserType.ADMIN.getValue())) {
            log.error("普通用户尝试登录管理后台: id-{}, 账号-{}, 昵称-{}, 姓名-{}", user.getId(), user.getStuNum(), user.getNickName(), user.getFullName());
            throw new ServiceException("非管理员账号, 禁止登录");
        }
        String password = SaSecureUtil.md5BySalt(loginDto.getPassword(), user.getSalt());
        if (!password.equals(user.getPassword())) {
            saveLoginLog(loginDto, null, request, false);
            Long count = redisTemplate.opsForValue().increment(CacheConstants.PWD_ERR_CNT_KEY + loginDto.getStuNum());
            if (count >= pwdErrTimes) {
                redisTemplate.opsForValue().set(CacheConstants.USER_FORBIDDEN + loginDto.getStuNum(), "lock");
                redisTemplate.expire(CacheConstants.USER_FORBIDDEN + loginDto.getStuNum(), pwdErrLockTime, TimeUnit.MINUTES);
                redisTemplate.delete(CacheConstants.PWD_ERR_CNT_KEY + loginDto.getStuNum());
                throw new ServiceException("您的账号密码错误次数超过" + pwdErrTimes + "次, 请" + pwdErrLockTime + "分钟后再试");
            }
            redisTemplate.expire(CacheConstants.PWD_ERR_CNT_KEY + loginDto.getStuNum(), pwdErrLockTime, TimeUnit.MINUTES);
            throw new ServiceException("密码错误");
        }
        String token = LoginUtils.login(user);
        final Long userId = user.getId();
        this.saveLoginLog(loginDto, userId, request, true);
        return token;
    }

    /**
     * 分页查询普通用户
     * @param userQueryDto
     * @return
     */
    @Override
    public IPage<SysUser> normalList(UserQueryDto userQueryDto) {
        return this.lambdaQuery()
                .eq(SysUser::getType, UserType.AVERAGE_USER.getValue())
                .likeRight(StringUtils.isNotBlank(userQueryDto.getStuNum()), SysUser::getStuNum, userQueryDto.getStuNum())
                .likeRight(StringUtils.isNotBlank(userQueryDto.getEmail()), SysUser::getEmail, userQueryDto.getEmail())
                .likeRight(StringUtils.isNotBlank(userQueryDto.getPhone()), SysUser::getPhone, userQueryDto.getPhone())
                .like(StringUtils.isNotBlank(userQueryDto.getFullName()), SysUser::getFullName, userQueryDto.getFullName())
                .select(SysUser::getId,
                        SysUser::getCreateTime,
                        SysUser::getNickName,
                        SysUser::getFullName,
                        SysUser::getLastLogin,
                        SysUser::getPhone,
                        SysUser::getSex,
                        SysUser::getStuNum,
                        SysUser::getStatus,
                        SysUser::getEmail)
                .orderByDesc(SysUser::getCreateTime)
                .page(new Page<>(userQueryDto.getPageNo(), userQueryDto.getPageSize()));
    }

    /**
     * 修改用户状态
     * @param userstatusDto
     */
    @Override
    public void updateUserStatus(UserStatusDto userstatusDto) {
        if (!(userstatusDto.getStatus().equals(Constants.USER_STATUS_DISABLE) || userstatusDto.getStatus().equals(Constants.USER_STATUS_ENABLE))) {
            throw new ServiceException("状态值不合法");
        }
        this.lambdaUpdate()
                .eq(SysUser::getId, userstatusDto.getId())
                .set(SysUser::getStatus, userstatusDto.getStatus())
                .update();
    }

    /**
     * 分页查询管理员用户
     * @param userQueryDto
     * @return
     */
    @Override
    public IPage<SysUser> adminList(UserQueryDto userQueryDto) {
        return this.lambdaQuery()
                .eq(SysUser::getType, UserType.ADMIN.getValue())
                .like(StringUtils.isNotBlank(userQueryDto.getFullName()), SysUser::getFullName, userQueryDto.getFullName())
                .select(SysUser::getId,
                        SysUser::getCreateTime,
                        SysUser::getFullName,
                        SysUser::getLastLogin,
                        SysUser::getStuNum,
                        SysUser::getStatus,
                        SysUser::getEmail)
                .orderByDesc(SysUser::getCreateTime)
                .page(new Page<>(userQueryDto.getPageNo(), userQueryDto.getPageSize()));
    }

    /**
     * 修改管理员密码
     * @param pwd
     */
    @Override
    public void updateAdminPassword(String pwd) {
        String newPwd = RSAUtils.decryptByRsa(pwd);
        SysUser loginUser = LoginUtils.getLoginUser();
        SysUser user = new SysUser();
        user.setId(loginUser.getId())
                .setPassword(SaSecureUtil.md5BySalt(newPwd, loginUser.getSalt()));
        boolean success = this.updateById(user);
        if (!success) {
            throw new ServiceException("系统异常, 修改失败");
        }
        loginUser.setPassword(user.getPassword());
        LoginUtils.updateUserInfo(loginUser);
    }

    /**
     * 修改管理员信息
     * @param sysUser
     */
    @Override
    public void updateAdminInfo(UpdateUserInfoDto sysUser) {
        Long id = LoginUtils.getLoginUser().getId();
        SysUser updateInfo = new SysUser();
        updateInfo.setId(id);
        if (sysUser.getNickName() != null) {
            updateInfo.setNickName(sysUser.getNickName());
        }
        if (sysUser.getFullName() != null) {
            updateInfo.setFullName(sysUser.getFullName());
        }
        if (sysUser.getSex() != null) {
            updateInfo.setSex(sysUser.getSex());
        }
        if (sysUser.getStuNum() != null) {
            updateInfo.setStuNum(sysUser.getStuNum());
        }
        if (sysUser.getEmail() != null) {
            Long count = this.lambdaQuery()
                    .eq(SysUser::getEmail, sysUser.getEmail())
                    .count();
            if (count > 0) {
                throw new ServiceException("该邮箱已被绑定");
            }
            updateInfo.setEmail(sysUser.getEmail());
        }
        if (sysUser.getPhone() != null) {
            Long count = this.lambdaQuery()
                    .eq(SysUser::getPhone, sysUser.getPhone())
                    .count();
            if (count > 0) {
                throw new ServiceException("该手机号码已被绑定");
            }
            updateInfo.setPhone(sysUser.getPhone());
        }
        boolean success = this.updateById(updateInfo);
        if (!success) {
            throw new ServiceException("系统异常修改失败");
        }
        LoginUtils.updateUserInfo(this.getById(id));
    }

    /**
     * 添加管理员
     *
     * @param addAdminDto
     */
    @Override
    public void addAdmin(AddAdminDto addAdminDto) {
        SysUser user = this.baseMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getStuNum, addAdminDto.getStuNum())
                .or()
                .eq(SysUser::getEmail, addAdminDto.getEmail())
                .or()
                .eq(SysUser::getFullName, addAdminDto.getFullName()));
        if (Objects.nonNull(user)) {
            throw new ServiceException("该管理员已存在");
        }
        emailUtils.testEmailCanSend(addAdminDto.getEmail());
        String salt = RandomUtil.randomString(6);
        String password = RandomUtil.randomString(8);
        SysUser sysUser = new SysUser()
                .setStuNum(addAdminDto.getStuNum())
                .setEmail(addAdminDto.getEmail())
                .setFullName(addAdminDto.getFullName())
                .setPassword(SaSecureUtil.md5BySalt(password, salt))
                .setSalt(salt)
                .setType(UserType.ADMIN.getValue())
                .setStatus(Constants.USER_STATUS_ENABLE);
        boolean save = this.save(sysUser);
        if (!save) {
            throw new ServiceException("添加失败，稍后再试");
        }
        log.info("新增管理员， {}, {}, {}", sysUser.getStuNum(), sysUser.getEmail(), sysUser.getFullName());
        emailUtils.sendAdminAccountPassword(addAdminDto.getEmail(), password, addAdminDto.getStuNum());
    }

    /**
     * 日志记录
     *
     * @param param
     * @param userId
     * @param request
     * @param loginSuccess
     */
    private void saveLoginLog(LoginDto param, Long userId, HttpServletRequest request, boolean loginSuccess) {
        systemThreadPool.execute(() -> {
            if (userId != null) {
                this.lambdaUpdate()
                        .eq(SysUser::getId, userId)
                        .set(SysUser::getLastLogin, LocalDateTime.now())
                        .set(SysUser::getLoginIp, IpUtils.getIpAddr(request))
                        .update();
            }
            SysLog sysLog = new SysLog();
            sysLog.setTitle("用户登录")
                    .setType(Constants.LOG_TYPE_LOGIN)
                    .setRequestIp(IpUtils.getIpAddr(request))
                    .setRequestParam(JSONUtils.toJsonString(param))
                    .setStatus(loginSuccess ? BusinessStatus.SUCCESS : BusinessStatus.FAIL);
            sysLogService.save(sysLog);
            if (!loginSuccess) {
                log.info("账号: '{}', 尝试登录失败", param.getStuNum() == null ? param.getEmail() : param.getStuNum());
            }
        });
    }

    /**
     * 通过邮箱获取对应的用户
     * @param email
     * @return
     */
    private SysUser getUserByEmail(String email) {
        SysUser user = this.baseMapper.selectOne(
                Wrappers.<SysUser>lambdaQuery()
                        .eq(SysUser::getEmail, email)
        );
        if (user == null) {
            throw new ServiceException("账户不存在");
        }
        return user;
    }

    /**
     * 检查账号状态
     * @param user
     */
    private void checkUserStatus(SysUser user) {
        if (user.getStatus().equals(Constants.USER_STATUS_DISABLE)) {
            throw new ServiceException("账号已被禁用");
        }
    }

}
