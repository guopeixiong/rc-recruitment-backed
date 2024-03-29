package com.ruanchuang.controller.h5;

import com.ruanchuang.annotation.RateLimiter;
import com.ruanchuang.model.CommonResult;
import com.ruanchuang.service.ActivityInfoService;
import com.ruanchuang.service.CommonQaInfoService;
import com.ruanchuang.service.IndexIntroInfoService;
import com.ruanchuang.service.IndexRollingImageService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author guopx
 * @since 2023/11/30
 */
@Api(tags = "h5端通用接口")
@Validated
@RestController
@RequestMapping("/h5")
public class CommonController {

    @Autowired
    private CommonQaInfoService commonQaInfoService;

    @Autowired
    private IndexRollingImageService indexRollingImageService;

    @Autowired
    private IndexIntroInfoService indexIntroInfoService;

    @Autowired
    private ActivityInfoService activityInfoService;

    @ApiOperation("获取常见问题接口")
    @RateLimiter(key = "getForm", count = 1000, message = "服务器限流, 请稍后再试")
    @GetMapping("/getCommonQaInfo")
    public CommonResult getCommonQaInfo() {
        return CommonResult.ok(commonQaInfoService.getEnableCommonQaInfo());
    }

    @ApiOperation("获取首页轮播图")
    @GetMapping("/indexImage")
    public CommonResult getIndexImage() {
        return CommonResult.ok(indexRollingImageService.getIndexImage());
    }

    @ApiOperation("获取首页简介")
    @GetMapping("/indexIntroduction")
    public CommonResult getIndexIntroduction() {
        return CommonResult.ok(indexIntroInfoService.getIndexText());
    }

    @ApiOperation("获取活动详情")
    @GetMapping("/activity")
    public CommonResult getActivity(@RequestParam("id") Long id) {
        return CommonResult.ok(activityInfoService.getActivity(id));
    }

}
