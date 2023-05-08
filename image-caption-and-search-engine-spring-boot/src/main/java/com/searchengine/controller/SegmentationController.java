package com.searchengine.controller;

import com.alibaba.fastjson.JSON;
import com.searchengine.dto.DataDto;
import com.searchengine.entity.Segmentation;
import com.searchengine.service.SegmentationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;

/**
 * 分词操作
 */
@RestController
@RequestMapping("/seg")
@Slf4j
public class SegmentationController {


    @Autowired
    private SegmentationService segmentationService;

    @GetMapping("/getAll")
    public List<Segmentation> getAllSeg(){
        log.info("查询所有分词");
        List<Segmentation> segmentations = segmentationService.queryAllSeg();
        return segmentations;
    }

    /**
     * @Description: python调用接口
     * @Param:
     * @Return:
     * @Author: gxp
     * @Date: 2023/5/5
     * @ModifierAndOtherInfo：
     **/
    @PostMapping("/saveData")
    public String saveData(@RequestBody DataDto dto){
        HashMap<String, Object> map = new HashMap<>();
        log.info("接收到的数据为：url："+dto.url+", caption: "+dto.caption);
        // 1、接受py传过来的数据并保存数据库，
        // 2、清空segmentation表数据并删除0-99表
        // 3、调用test中的两个方法
        if (dto.url == null || "".equals(dto.url)){
            map.put("code",1);
            map.put("msg","url不能为空");
            return JSON.toJSONString(map);
        }
        if (dto.caption == null || "".equals(dto.caption)){
            map.put("code",1);
            map.put("msg","caption不能为空");
            return JSON.toJSONString(map);
        }
        segmentationService.saveData(dto.url,dto.caption);
        map.put("code",0);
        map.put("msg","操作成功");
        return JSON.toJSONString(map);
    }
}
