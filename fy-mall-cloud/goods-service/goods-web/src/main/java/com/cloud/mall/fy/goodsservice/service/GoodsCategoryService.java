package com.cloud.mall.fy.goodsservice.service;

import com.cloud.mall.fy.goodsservice.controller.vo.FirstLevelCategoryVO;

import java.util.List;

/**
 * 商品分类服务层
 */
public interface GoodsCategoryService {

    /**
     * 获取首页分类数据
     */
    List<FirstLevelCategoryVO> getCategoriesForIndex();

}
