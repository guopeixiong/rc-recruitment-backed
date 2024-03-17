package com.ruanchuang.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ruanchuang.domain.ActivityInfo;

/**
 * @Author guopeixiong
 * @Date 2024/3/16
 * @Email peixiongguo@163.com
 */
public interface ActivityInfoService extends IService<ActivityInfo> {

    /**
     * 根据id查询活动信息
     * @param id
     * @return
     */
    ActivityInfo getActivity(Long id);
}
