package com.cloud.mall.fy.api.factory;

import com.cloud.mall.fy.api.RemoteGoodsService;
import com.cloud.mall.fy.api.dto.GoodsDetailDto;
import com.cloud.mall.fy.api.dto.StockNumDto;
import com.ruoyi.common.core.domain.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 商品服务降级处理
 *
 * @author FangYuan
 * @since 2022-11-10 20:29:48
 */
@Slf4j
@Component
public class RemoteGoodsFallbackFactory implements FallbackFactory<RemoteGoodsService> {

    @Override
    public RemoteGoodsService create(Throwable cause) {
        log.error("商品服务调用失败: {}", cause.getMessage());

        return new RemoteGoodsService() {
            @Override
            public R<GoodsDetailDto> getGoodsInfoById(Long goodsId, String source) {
                return R.fail("获取商品信息失败: " + cause.getMessage());
            }

            @Override
            public R<List<GoodsDetailDto>> getGoodsListById(List<Long> goodsIds, String source) {
                return R.fail("获取商品列表信息失败: " + cause.getMessage());
            }

            @Override
            public R<Boolean> reduceCount(List<StockNumDto> stockNumDtoList, String source) {
                return R.fail("扣减商品库存失败: " + cause.getMessage());
            }
        };
    }
}
