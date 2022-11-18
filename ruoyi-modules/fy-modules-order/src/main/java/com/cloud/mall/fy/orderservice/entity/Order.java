package com.cloud.mall.fy.orderservice.entity;

import com.ruoyi.common.datasource.domain.BaseEntityForMall;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class Order extends BaseEntityForMall {

    private String orderNo;

    private Long userId;

    private Integer totalPrice;

    private Integer payStatus;

    private Integer payType;

    private LocalDateTime payTime;

    private Integer orderStatus;

    private String extraInfo;

}