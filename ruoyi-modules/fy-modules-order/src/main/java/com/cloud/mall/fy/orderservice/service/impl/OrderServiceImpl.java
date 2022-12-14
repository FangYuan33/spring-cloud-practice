package com.cloud.mall.fy.orderservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cloud.mall.fy.api.RemoteGoodsService;
import com.cloud.mall.fy.api.RemoteShoppingCartService;
import com.cloud.mall.fy.api.dto.*;
import com.cloud.mall.fy.orderservice.controller.param.OrderPayParam;
import com.cloud.mall.fy.orderservice.controller.param.OrderQueryParam;
import com.cloud.mall.fy.orderservice.controller.param.OrderSaveParam;
import com.cloud.mall.fy.orderservice.dao.OrderMapper;
import com.cloud.mall.fy.orderservice.entity.OrderHeader;
import com.cloud.mall.fy.orderservice.entity.OrderAddress;
import com.cloud.mall.fy.orderservice.entity.OrderItem;
import com.cloud.mall.fy.orderservice.entity.UserAddress;
import com.cloud.mall.fy.orderservice.service.OrderAddressService;
import com.cloud.mall.fy.orderservice.service.OrderItemService;
import com.cloud.mall.fy.orderservice.service.OrderService;
import com.cloud.mall.fy.orderservice.service.UserAddressService;
import com.ruoyi.common.core.constant.SecurityConstants;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.core.enums.GoodsSellStatusEnum;
import com.ruoyi.common.core.enums.OrderStatusEnum;
import com.ruoyi.common.core.enums.PayStatusEnum;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.core.utils.StringUtils;
import com.ruoyi.common.core.utils.bean.BeanUtils;
import com.ruoyi.common.core.utils.feign.OpenFeignResultUtil;
import com.ruoyi.common.core.utils.uuid.Seq;
import com.ruoyi.common.security.utils.SecurityUtils;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, OrderHeader> implements OrderService {

    @Autowired
    private OrderItemService orderItemService;
    @Autowired
    private UserAddressService userAddressService;
    @Autowired
    private OrderAddressService orderAddressService;
    @Autowired
    private RemoteShoppingCartService shoppingCartService;
    @Autowired
    private RemoteGoodsService goodsService;

    @Override
    public List<OrderDto> listByCondition(OrderQueryParam orderQueryParam) {
        LambdaQueryWrapper<OrderHeader> queryWrapper = new QueryWrapper<OrderHeader>().lambda()
                .eq(StringUtils.isNotEmpty(orderQueryParam.getOrderNo()), OrderHeader::getOrderNo, orderQueryParam.getOrderNo())
                .eq(orderQueryParam.getOrderStatus() != null, OrderHeader::getOrderStatus, orderQueryParam.getOrderStatus());
        List<OrderHeader> orderHeaders = baseMapper.selectList(queryWrapper);

        return BeanUtils.copyList(orderHeaders, OrderDto.class);
    }

    @Override
    public OrderDetailDto getOrderDetailById(Long orderId) {
        if (orderId == null) {
            throw new ServiceException("????????????");
        }

        // ?????????????????????
        OrderHeader orderHeader = baseMapper.selectById(orderId);
        List<OrderItemDto> orderItems = orderItemService.getOrderItemsByOrderId(orderId);

        // ????????????
        OrderDetailDto result = BeanUtils.copyProperties2(orderHeader, OrderDetailDto.class);
        result.setOrderItemList(orderItems);

        return result;
    }

    @SuppressWarnings("unchecked")
    @Transactional
    @GlobalTransactional
    @Override
    public void saveOrder(OrderSaveParam orderSaveParam) {
        // ????????????
        if (orderSaveParam.getAddressId() == null || orderSaveParam.getCartItemIds() == null
                || orderSaveParam.getCartItemIds().isEmpty()) {
            throw new ServiceException("????????????");
        }

        // ?????????????????????
        R<List<ShoppingCartItemDto>> shoppingCartResult = shoppingCartService
                .toSettle(orderSaveParam.getCartItemIds(), SecurityConstants.INNER);
        List<ShoppingCartItemDto> shoppingCartItems =
                (List<ShoppingCartItemDto>) OpenFeignResultUtil.processFeignResult(shoppingCartResult);

        // map -> key: goodsId, value: goodsCount
        Map<Long, Integer> goodsCountMap = shoppingCartItems.stream()
                .collect(Collectors.toMap(ShoppingCartItemDto::getGoodsId, ShoppingCartItemDto::getGoodsCount));
        List<Long> goodsIdList = shoppingCartItems.stream().map(ShoppingCartItemDto::getGoodsId).collect(Collectors.toList());

        // ??????????????????
        R<List<GoodsDetailDto>> goodsResult = goodsService.getGoodsListById(goodsIdList, SecurityConstants.INNER);
        List<GoodsDetailDto> goodsList = (List<GoodsDetailDto>) OpenFeignResultUtil.processFeignResult(goodsResult);

        // ?????????????????????????????????
        checkGoodsStatusAndStock(goodsList, goodsCountMap);

        // ????????????
        reduceGoodsCount(goodsCountMap);

        // ????????????
        saveOrder(goodsList, goodsCountMap, orderSaveParam.getAddressId());

        // ????????????????????????
        shoppingCartService.deleteShoppingCartItem(orderSaveParam.getCartItemIds(), SecurityConstants.INNER);
    }

    /**
     * ?????????????????????????????????????????????
     */
    private void checkGoodsStatusAndStock(List<GoodsDetailDto> goodsList, Map<Long, Integer> goodsCountMap) {
        for (GoodsDetailDto goodsDetailDto : goodsList) {
            // ???????????????????????????????????????
            if (!GoodsSellStatusEnum.PUT_UP.getValue().equals(goodsDetailDto.getGoodsSellStatus())) {
                throw new ServiceException(goodsDetailDto.getGoodsName() + "??????????????????????????????");
            }
            // ????????????????????????
            Integer reduceCount = goodsCountMap.get(goodsDetailDto.getId());
            if (goodsDetailDto.getStockNum() < reduceCount) {
                throw new ServiceException(goodsDetailDto.getGoodsName() + "????????????");
            }
        }
    }

    /**
     * ??????????????????
     */
    private void reduceGoodsCount(Map<Long, Integer> goodsCountMap) {
        ArrayList<StockNumDto> stockNumDtoList = new ArrayList<>(goodsCountMap.size());
        for (Map.Entry<Long, Integer> idAndCount : goodsCountMap.entrySet()) {
            stockNumDtoList.add(new StockNumDto(idAndCount.getKey(), idAndCount.getValue()));
        }
        R<Boolean> reduceResult = goodsService.reduceCount(stockNumDtoList, SecurityConstants.INNER);
        if (R.isError(reduceResult)) {
            throw new ServiceException("????????????????????????");
        }
    }

    /**
     * ????????????
     * 1. ???????????????
     * 2. ??????????????????
     * 3. ??????????????????
     */
    private void saveOrder(List<GoodsDetailDto> goodsList, Map<Long, Integer> goodsCountMap, Long userAddressId) {
        // ?????????????????????
        OrderHeader orderHeader = initialOrderInfo(goodsList, goodsCountMap);
        if (baseMapper.insert(orderHeader) > 0) {
            // ???????????????????????????
            ArrayList<OrderItem> orderItems = initialOrderItemList(goodsList, goodsCountMap, orderHeader.getId());
            orderItemService.saveBatch(orderItems);

            // ???????????????????????????
            OrderAddress orderAddress = initialOrderAddress(orderHeader.getId(), userAddressId);
            orderAddressService.save(orderAddress);
        }
    }

    /**
     * ????????????????????????
     */
    private OrderHeader initialOrderInfo(List<GoodsDetailDto> goodsList, Map<Long, Integer> goodsCountMap) {
        String orderNum = Seq.getOrderCode();
        int totalPrice = 0;
        for (GoodsDetailDto goodsDetailDto : goodsList) {
            totalPrice += goodsDetailDto.getSellingPrice() * goodsCountMap.get(goodsDetailDto.getId());
        }
        // ??????????????????ID???????????????????????????
        return new OrderHeader().setOrderNo(orderNum).setUserId(SecurityUtils.getUserId()).setTotalPrice(totalPrice)
                .setOrderStatus(OrderStatusEnum.WAIT_PAY.getValue());
    }

    /**
     * ?????????????????????
     */
    private ArrayList<OrderItem> initialOrderItemList(List<GoodsDetailDto> goodsList, Map<Long, Integer> goodsCountMap,
                                                      Long orderId) {
        ArrayList<OrderItem> orderItems = new ArrayList<>(goodsList.size());
        for (GoodsDetailDto goodsDetailDto : goodsList) {
            // ??????ID?????????ID??????????????????????????????????????????????????????
            OrderItem orderItem = new OrderItem().setOrderId(orderId).setGoodsId(goodsDetailDto.getId())
                    .setGoodsName(goodsDetailDto.getGoodsName()).setGoodsCoverImg(goodsDetailDto.getGoodsCoverImg())
                    .setSellingPrice(goodsDetailDto.getSellingPrice()).setGoodsCount(goodsCountMap.get(goodsDetailDto.getId()));
            orderItems.add(orderItem);
        }

        return orderItems;
    }

    /**
     * ???????????????????????????
     */
    private OrderAddress initialOrderAddress(Long orderId , Long userAddressId) {
        UserAddress userAddress = userAddressService.getById(userAddressId);
        // ??????ID??????????????????????????????????????????
        OrderAddress orderAddress = new OrderAddress().setOrderId(orderId);
        BeanUtils.copyProperties(userAddress, orderAddress);

        return orderAddress;
    }

    @Override
    public void batchChangeStatusFromTo(List<Long> idList, OrderStatusEnum from, OrderStatusEnum to) {
        if (!CollectionUtils.isEmpty(idList)) {
            List<OrderHeader> orderHeaders = baseMapper.selectBatchIds(idList);

            // ??????????????????
            multipleModifyOrderStatus(orderHeaders, from, to);
        }
    }

    @Override
    public void cancelOrderByIda(Long orderId) {
        OrderHeader orderHeader = baseMapper.selectById(orderId);

        // ????????????????????????????????????
        if (!SecurityUtils.getUserId().equals(orderHeader.getUserId())) {
            throw new ServiceException("???????????????");
        }
        // ????????????
        if (OrderStatusEnum.DEAL_SUCCESS.getValue().compareTo(orderHeader.getOrderStatus()) <= 0) {
            throw new ServiceException("????????????????????????");
        }

        // ????????????
        multipleModifyOrderStatus(Collections.singletonList(orderHeader), null, OrderStatusEnum.CLOSED_BY_HAND);
    }

    @Override
    public void finishOrder(Long orderId) {
        OrderHeader orderHeader = baseMapper.selectById(orderId);

        // ????????????????????????????????????
        if (!SecurityUtils.getUserId().equals(orderHeader.getUserId())) {
            throw new ServiceException("???????????????");
        }
        // ????????????
        if (!OrderStatusEnum.SEND.getValue().equals(orderHeader.getOrderStatus())) {
            throw new ServiceException("??????????????????????????????");
        }

        multipleModifyOrderStatus(Collections.singletonList(orderHeader), null, OrderStatusEnum.DEAL_SUCCESS);
    }

    @Override
    public void paySuccess(OrderPayParam orderPayParam) {
        OrderHeader orderHeader = baseMapper.selectById(orderPayParam.getId());

        if (orderHeader != null) {
            // ????????????
            if (!OrderStatusEnum.WAIT_PAY.getValue().equals(orderHeader.getOrderStatus())) {
                throw new ServiceException("??????????????????");
            }
            // ??????????????????????????????????????????
            orderHeader.setPayType(orderPayParam.getPayType()).setPayTime(LocalDateTime.now())
                    .setPayStatus(PayStatusEnum.PAY_SUCCESS.getPayStatus());
            // ??????????????????????????????
            multipleModifyOrderStatus(Collections.singletonList(orderHeader), OrderStatusEnum.WAIT_PAY, OrderStatusEnum.ALREADY_PAY);
        }
    }

    /**
     * ????????????????????????
     *
     * @param from ???????????????????????????????????????????????????????????????????????????????????????
     * @param to   ????????????
     */
    private void multipleModifyOrderStatus(List<OrderHeader> orderHeaders, OrderStatusEnum from, OrderStatusEnum to) {
        for (OrderHeader orderHeader : orderHeaders) {
            if (from != null && from.getValue().equals(orderHeader.getOrderStatus())) {
                log.info("{} {} -> {}", orderHeader.getOrderNo(), from.getName(), to.getName());
            } else {
                OrderStatusEnum orderStatusEnum = OrderStatusEnum.parse(orderHeader.getOrderStatus());
                log.info("{} {} -> {}", orderHeader.getOrderNo(), orderStatusEnum.getName(), to.getName());
            }
            orderHeader.setOrderStatus(to.getValue());
            baseMapper.updateById(orderHeader);
        }
    }
}
