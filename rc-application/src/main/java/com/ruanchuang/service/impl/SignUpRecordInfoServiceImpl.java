package com.ruanchuang.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ruanchuang.domain.SignUpRecordInfo;
import com.ruanchuang.domain.TemplateQuestionOptions;
import com.ruanchuang.domain.dto.BaseQueryDto;
import com.ruanchuang.domain.vo.SignUpDetailVo;
import com.ruanchuang.enums.Constants;
import com.ruanchuang.exception.ServiceException;
import com.ruanchuang.mapper.SignUpRecordInfoMapper;
import com.ruanchuang.service.SignUpProcessService;
import com.ruanchuang.service.SignUpRecordInfoService;
import com.ruanchuang.service.TemplateQuestionOptionsService;
import com.ruanchuang.utils.LoginUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * <p>
 * 报名记录信息表 服务实现类
 * </p>
 *
 * @author guopeixiong
 * @since 2023-08-01
 */
@Service
public class SignUpRecordInfoServiceImpl extends ServiceImpl<SignUpRecordInfoMapper, SignUpRecordInfo> implements SignUpRecordInfoService {

    @Autowired
    private TemplateQuestionOptionsService templateQuestionOptionsService;

    @Autowired
    private SignUpProcessService signUpProcessService;

    /**
     * 用户分页查询报名记录列表
     *
     * @param baseQueryDto
     * @return
     */
    @Override
    public IPage<SignUpRecordInfo> queryUserSignUpRecord(BaseQueryDto baseQueryDto) {
        Page<SignUpRecordInfo> page = this.lambdaQuery()
                .eq(SignUpRecordInfo::getUserId, LoginUtils.getLoginUser().getId())
                .select(SignUpRecordInfo::getId,
                        SignUpRecordInfo::getCreateTime,
                        SignUpRecordInfo::getTemplateId,
                        SignUpRecordInfo::getProcessId,
                        SignUpRecordInfo::getCurrentProcessStatusId)
                .orderByDesc(SignUpRecordInfo::getCreateTime)
                .page(new Page<>(baseQueryDto.getPageNum(), baseQueryDto.getPageSize()));
        page.getRecords().stream().forEach(record ->
            record.setCurrentProcess(signUpProcessService.getProcessStatusNameById(record.getProcessId(), record.getCurrentProcessStatusId()))
        );
        return page;
    }

    /**
     * 查询报名详情
     *
     * @param id
     * @return
     */
    @Override
    public List<SignUpDetailVo> querySignUpDetail(Long id) {
        if (Objects.isNull(id)) {
            throw new ServiceException("非法入参");
        }
        // 作答报名表模板id
        Long templateId = Optional.ofNullable(this.baseMapper.selectOne(Wrappers.<SignUpRecordInfo>lambdaQuery()
                        .eq(SignUpRecordInfo::getId, id)
                        .select(SignUpRecordInfo::getTemplateId)))
                .map(SignUpRecordInfo::getTemplateId)
                .orElseThrow(() -> new ServiceException("报名记录不存在"));
        Long userId = LoginUtils.getLoginUser().getId();
        List<SignUpDetailVo> signUpDetailVos = this.baseMapper.querySignUpDetail(id, templateId, userId);
        // 将选择题目选项id放入集合
        List<String> optIds = signUpDetailVos.stream().filter(o -> o.getType().equals(Constants.SIGN_UP_FORM_QUESTION_TYPE_SINGLE_CHOICE) && Objects.nonNull(o.getOptAnswer()))
                .map(SignUpDetailVo::getOptAnswer)
                .collect(Collectors.toList());
        signUpDetailVos.stream().filter(o -> o.getType().equals(Constants.SIGN_UP_FORM_QUESTION_TYPE_MULTIPLE_CHOICE) && Objects.nonNull(o.getOptAnswer()))
                .map(SignUpDetailVo::getOptAnswer)
                .forEach(ids -> Arrays.stream(ids.split(",")).forEach(optId -> optIds.add(optId)));
        if (optIds.isEmpty()) {
            return signUpDetailVos;
        }
        List<TemplateQuestionOptions> optContents = templateQuestionOptionsService.lambdaQuery()
                .in(TemplateQuestionOptions::getId, optIds)
                .select(TemplateQuestionOptions::getContent,
                        TemplateQuestionOptions::getId,
                        TemplateQuestionOptions::getQuestionId)
                .list();
        signUpDetailVos.stream().filter(o -> Objects.nonNull(o.getOptAnswer()) && (o.getType().equals(Constants.SIGN_UP_FORM_QUESTION_TYPE_SINGLE_CHOICE) || o.getType().equals(Constants.SIGN_UP_FORM_QUESTION_TYPE_MULTIPLE_CHOICE)))
                .forEach(record -> {
                    switch (record.getType().intValue()) {
                        case Constants.SIGN_UP_FORM_QUESTION_TYPE_SINGLE_CHOICE:
                            // 此处有坑, 因为数据库中存储的选项id是字符串, 所以需要转换成字符串, 也就是下面的o.getId().toString()
                            record.setOptAnswer(optContents.stream().filter(o -> o.getId().toString().equals(record.getOptAnswer())).findFirst().map(TemplateQuestionOptions::getContent).orElse(null));
                            break;
                        case Constants.SIGN_UP_FORM_QUESTION_TYPE_MULTIPLE_CHOICE:
                            record.setOptAnswer(optContents.stream().filter(o -> o.getQuestionId().equals(record.getQuestionId())).map(TemplateQuestionOptions::getContent).collect(Collectors.joining(",")));
                    }
                });
        return signUpDetailVos;
    }

}
