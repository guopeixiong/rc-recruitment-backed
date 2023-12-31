package com.ruanchuang.domain;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 通知发送记录表
 * </p>
 *
 * @author guopeixiong
 * @since 2023-08-01
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("notice_send_record")
@ApiModel(value = "NoticeSendRecord对象", description = "通知发送记录表")
public class NoticeSendRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("id")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    @ApiModelProperty("目标用户id")
    private Long userId;

    @ApiModelProperty("目标用户姓名")
    private String username;

    @ApiModelProperty("通知内容")
    private String content;

    @ApiModelProperty("消息状态;0.未发送 1.已发送 2.未读 3.已读")
    private Integer status;

    @ApiModelProperty("发送类型;0.立即发送 1.定时发送")
    private Integer sendType;

    @ApiModelProperty("定时发送时间")
    private LocalDateTime planSendTime;

    @ApiModelProperty("实际发送时间")
    private LocalDateTime sendTime;

    @ApiModelProperty("创建人")
    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    @ApiModelProperty("创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @ApiModelProperty("更新人")
    @TableField(fill = FieldFill.UPDATE)
    private String updateBy;

    @ApiModelProperty("更新时间")
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updateTime;

    @ApiModelProperty("是否删除;0.否 1.是")
    @TableLogic
    private Integer isDelete;

    @ApiModelProperty("版本号")
    @Version
    private Integer version;
}
