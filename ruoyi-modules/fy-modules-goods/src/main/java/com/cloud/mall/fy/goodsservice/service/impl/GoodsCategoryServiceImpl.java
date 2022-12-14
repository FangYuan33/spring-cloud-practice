package com.cloud.mall.fy.goodsservice.service.impl;

import com.cloud.mall.fy.goodsservice.controller.param.GoodsCategoryAddParam;
import com.cloud.mall.fy.goodsservice.controller.param.GoodsCategoryEditParam;
import com.cloud.mall.fy.api.dto.FirstLevelCategoryDto;
import com.cloud.mall.fy.api.dto.SecondLevelCategoryDto;
import com.cloud.mall.fy.api.dto.ThirdLevelCategoryDto;
import com.cloud.mall.fy.goodsservice.dao.GoodsCategoryMapper;
import com.cloud.mall.fy.goodsservice.entity.GoodsCategory;
import com.cloud.mall.fy.goodsservice.service.GoodsCategoryService;
import com.ruoyi.common.core.enums.CategoryLevelEnum;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.core.utils.bean.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GoodsCategoryServiceImpl implements GoodsCategoryService {

    @Resource
    private GoodsCategoryMapper goodsCategoryMapper;


    @Override
    public List<FirstLevelCategoryDto> getCategoriesForIndex() {
        // 获取一级分类
        List<GoodsCategory> firstLeverCategories = goodsCategoryMapper.selectByLevelAndInParentIds(
                CategoryLevelEnum.LEVEL_ONE.getLevel(), Collections.singletonList(0L));
        List<Long> firstIds = firstLeverCategories.stream().map(GoodsCategory::getId).collect(Collectors.toList());
        // 获取二级分类
        List<GoodsCategory> secondLevelCategories = goodsCategoryMapper.selectByLevelAndInParentIds(
                CategoryLevelEnum.LEVEL_TWO.getLevel(), firstIds);
        List<Long> secondIds = secondLevelCategories.stream().map(GoodsCategory::getId).collect(Collectors.toList());
        // 获取三级分类
        List<GoodsCategory> thirdLevelCategories = goodsCategoryMapper.selectByLevelAndInParentIds(
                CategoryLevelEnum.LEVEL_THREE.getLevel(), secondIds);

        // 二级分类视图
        List<SecondLevelCategoryDto> secondLevelCategoryDtoList =
                getSecondLevelCategoryVOWithThirdLevelCategory(thirdLevelCategories, secondLevelCategories);

        // 处理并返回一级分类视图
        return getIndexCategoryVOWithSecondLevelCategory(secondLevelCategoryDtoList, firstLeverCategories);
    }

    /**
     * 将对应的三级分类挂到所属的二级分类并返回二级分类视图
     */
    private List<SecondLevelCategoryDto> getSecondLevelCategoryVOWithThirdLevelCategory(
                                                                List<GoodsCategory> thirdLevelCategories,
                                                                List<GoodsCategory> secondLevelCategories) {
        // 三级分类根据二级父类分类
        Map<Long, List<GoodsCategory>> thirdMap = thirdLevelCategories.stream()
                .collect(Collectors.groupingBy(GoodsCategory::getParentId));

        // 处理二级分类，将二级分类下挂对应的三级分类
        List<SecondLevelCategoryDto> secondLevelCategoryDtos = new ArrayList<>();
        for (GoodsCategory secondLevelCategory : secondLevelCategories) {
            SecondLevelCategoryDto secondLevelCategoryDto =
                    BeanUtils.copyProperties2(secondLevelCategory, SecondLevelCategoryDto.class);

            // 如果该二级分类下有三级分类数据则处理
            if (thirdMap.containsKey(secondLevelCategory.getId())) {
                // 根据二级分类的id取出thirdLevelCategoryMap分组中的三级分类list
                List<GoodsCategory> tempGoodsCategories = thirdMap.get(secondLevelCategory.getId());
                secondLevelCategoryDto.setThirdLevelCategoryDtos(
                        BeanUtils.copyList(tempGoodsCategories, ThirdLevelCategoryDto.class));
            }
            secondLevelCategoryDtos.add(secondLevelCategoryDto);
        }

        return secondLevelCategoryDtos;
    }

    private ArrayList<FirstLevelCategoryDto> getIndexCategoryVOWithSecondLevelCategory(
                                                                List<SecondLevelCategoryDto> secondLevelCategoryDtoList,
                                                                List<GoodsCategory> firstLeverCategories) {
        // 处理一级分类，对应挂靠它的所属二级分类
        ArrayList<FirstLevelCategoryDto> firstLevelCategoryDtos = new ArrayList<>();
        Map<Long, List<SecondLevelCategoryDto>> secondMap = secondLevelCategoryDtoList.stream()
                .collect(Collectors.groupingBy(SecondLevelCategoryDto::getParentId));
        for (GoodsCategory firstLeverCategory : firstLeverCategories) {
            FirstLevelCategoryDto firstLevelCategoryDto = BeanUtils.copyProperties2(firstLeverCategory, FirstLevelCategoryDto.class);

            if (secondMap.containsKey(firstLeverCategory.getId())) {
                List<SecondLevelCategoryDto> tempSecondCategories = secondMap.get(firstLeverCategory.getId());
                firstLevelCategoryDto.setSecondLevelCategoryDtos(tempSecondCategories);
            }
            firstLevelCategoryDtos.add(firstLevelCategoryDto);
        }

        return firstLevelCategoryDtos;
    }

    @Override
    public void save(GoodsCategoryAddParam goodsCategoryAddParam) {
        GoodsCategory goodsCategory = BeanUtils.copyProperties2(goodsCategoryAddParam, GoodsCategory.class);
        goodsCategoryMapper.insert(goodsCategory);
    }

    @Override
    public void edit(GoodsCategoryEditParam goodsCategoryEditParam) {
        GoodsCategory goodsCategory = BeanUtils.copyProperties2(goodsCategoryEditParam, GoodsCategory.class);
        goodsCategoryMapper.updateById(goodsCategory);
    }

    @Override
    public void deleteByIds(String ids) {
        if (StringUtils.hasText(ids)) {
            String[] categoryIds = ids.split(",");

            for (String categoryId : categoryIds) {
                goodsCategoryMapper.deleteById(categoryId);
            }
        } else {
            throw new ServiceException("参数异常");
        }
    }

    @Override
    public GoodsCategory getById(Long id) {
        return goodsCategoryMapper.selectById(id);
    }
}
