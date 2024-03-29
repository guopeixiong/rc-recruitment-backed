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
 * 报名记录信息表
 * </p>
 *
 * @author guopeixiong
 * @since 2023-08-01
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("sign_up_record_info")
@ApiModel(value = "SignUpRecordInfo对象", description = "报名记录信息表")
public class SignUpRecordInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("id")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    @ApiModelProperty("用户id")
    private Long userId;

    @ApiModelProperty("用户姓名")
    private String userName;

    @ApiModelProperty("所填写模板id")
    private Long templateId;

    @ApiModelProperty("模板名称")
    private String templateName;

    @ApiModelProperty("流程id")
    private Long processId;

    @ApiModelProperty("当前流程状态id")
    private Long currentProcessStatusId;

    @ApiModelProperty("当前流程")
    @TableField(exist = false)
    private String currentProcess;

    @ApiModelProperty("流程结束 0.否 1.是")
    private Integer processEnd;

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
