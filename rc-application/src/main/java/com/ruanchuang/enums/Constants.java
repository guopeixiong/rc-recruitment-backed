package com.ruanchuang.enums;

/**
 * 常用常量值
 * @Author guopeixiong
 * @Date 2023/8/19
 * @Email peixiongguo@163.com
 */
public interface Constants {

    /**
     * 报名表模板状态 启动
     */
    Integer SIGN_UP_FORM_TEMPLATE_STATUS_ENABLE = 1;

    /**
     * 报名表模板状态 未启动
     */
    Integer SIGN_UP_FORM_TEMPLATE_STATUS_DISABLE = 0;

    /**
     * 报名表类型 报名
     */
    Integer SIGN_UP_FROM_TYPE_SIGN_UP = 0;

    /**
     * 报名表类型 活动
     */
    Integer SIGN_UP_FROM_TYPE_ACTIVITY = 1;

    /**
     * 报名表问题类型 填空题
     */
    Integer SIGN_UP_FORM_QUESTION_TYPE_TEXT = 0;

    /**
     * 报名表问题类型 单选题
     */
    int SIGN_UP_FORM_QUESTION_TYPE_SINGLE_CHOICE = 1;

    /**
     * 报名表问题类型 多选题
     */
    int SIGN_UP_FORM_QUESTION_TYPE_MULTIPLE_CHOICE = 2;

    /**
     * 问题类型-必答
     */
    Integer QUESTION_TYPE_REQUIRE = 1;

    /**
     * 问题类型-非必答
     */
    Integer QUESTION_TYPE_NO_REQUIRE = 0;

    /**
     * 答案类型-文本
     */
    Integer QUESTION_ANSWER_TYPE_TEXT = 0;

    /**
     * 答案类型-选项
     */
    Integer QUESTION_ANSWER_TYPE_OPTION = 1;

    /**
     * 常见问题状态-启用
     */
    Integer COMMON_QA_INFO_STATUS_ENABLE = 1;

    /**
     * 咨询信息状态-未回复
     */
    Integer CONSULTING_INFO_STATUS_UN_REPLY = 0;

    /**
     * 咨询信息状态-已回复
     */
    Integer CONSULTING_INFO_STATUS_REPLY = 1;

    /**
     * 报名流程状态-启用
     */
    Integer SIGN_UP_PROCESS_STATUS_ENABLE = 1;

    /**
     * 报名流程状态-未启用
     */
    Integer SIGN_UP_PROCESS_STATUS_DISABLE = 0;

    /**
     * 报名流程状态-已结束
     */
    Integer SIGN_UP_PROCESS_STATUS_END = 1;

    /**
     * 保存邮件模板
     */
    Integer SAVE_EMAIL_TEMPLATE = 1;


    /**
     * 日志类型 用户
     */
    int LOG_TYPE_USER = 0;

    /**
     * 日志类型 后台
     */
    int LOG_TYPE_ADMIN = 1;

    /**
     * 日志类型 登录
     */
    int LOG_TYPE_LOGIN = 2;

    /**
     * 首页简介状态 启用
     */
    Integer INDEX_INTRO_STATUS_ENABLE = 1;

    /**
     * 首页简介状态 未启用
     */
    Integer INDEX_INTRO_STATUS_DISABLE = 0;

    /**
     * 首页图片状态 未启用
     */
    Integer INDEX_IMAGE_DISABLE = 0;

    /**
     * 首页图片状态 启用
     */
    Integer INDEX_IMAGE_ENABLE = 1;
}
