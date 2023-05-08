package com.searchengine.service.impl;

import com.searchengine.common.SegResult;
import com.searchengine.dao.RecordSegDao;
import com.searchengine.dao.SegmentationDao;
import com.searchengine.entity.RecordSeg;
import com.searchengine.entity.Segmentation;
import com.searchengine.service.AddAllSegService;
import com.searchengine.service.RecordSegService;
import com.searchengine.service.SegmentationService;
import com.searchengine.utils.RedisUtil_db0;
import com.searchengine.utils.jieba.keyword.TFIDFAnalyzer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
@Slf4j
public class SegmentationServiceImpl implements SegmentationService {

    TFIDFAnalyzer tfidfAnalyzer = new TFIDFAnalyzer();
    @Autowired
    private SegmentationDao segmentationDao;
    @Autowired
    private RecordSegDao recordSegDao;
    @Autowired
    private RecordSegService recordSegService;
    @Autowired
    private RedisUtil_db0 redisUtil;
    @Autowired
    private AddAllSegService addAllSegService;

    @Override
    public List<Segmentation> queryAllSeg() {
        return segmentationDao.selectAllSeg();
    }

    @Override
    public Boolean addSeg(String word, Integer dataId, Double tidifValue) {

//        Segmentation segmentation = new Segmentation();
        Segmentation seg = segmentationDao.selectOneSeg(word);
        if (seg != null) {
            //分词不存在 加入分词表
            segmentationDao.insertSeg(word);
        }


        //加入关系表
        RecordSeg recordSeg = new RecordSeg();
        recordSeg.setSegId(seg.getId());
        recordSeg.setDataId(dataId);
        recordSeg.setTidifValue(tidifValue);
        RecordSeg rs = recordSegDao.selectOneRecordSeg(dataId, seg.getId());
        if (rs == null) {
            recordSeg.setCount(1);
            recordSegDao.insertRecordSeg(recordSeg);
        } else {
            int count = rs.getCount();
            //文中出现次数>1
            recordSeg.setCount(++count);
            recordSegDao.updateRecordSeg(recordSeg);
        }

        return true;
    }

    @Override
    public Boolean addSeg(List<SegResult> segResults) {
        /*segResult对象列表->查询一遍分词库->已有的分词对象列表             ->关系表列表->存入关系表
                                         ->没有的分词对象列表->加入分词库 ↗
          */
        List<RecordSeg> recordSegList = new ArrayList<>();
        //查！ 不知道咋查一次  还是先挨个查吧
        for (SegResult segResult : segResults) {
            RecordSeg recordSeg = new RecordSeg();
            recordSeg.setDataId(segResult.getRecordId());
            recordSeg.setTidifValue(segResult.getTidifValue());
            recordSeg.setCount(segResult.getCount());
            String word = segResult.getWord();
            if (redisUtil.hasKey("seg_" + word)) {
                int segId = (int) redisUtil.get("seg_" + word);
                recordSeg.setSegId(segId);
                recordSegList.add(recordSeg);
            } else {

                Segmentation seg = segmentationDao.selectOneSeg(word);
                if (seg == null) {
                    //分词不存在 加入分词表
                    segmentationDao.insertSeg(segResult.getWord());//此处不知道怎么直接返回主键 试了几个方法都失败了

                    seg = segmentationDao.selectOneSeg(word);//导致现在又多查了一次
                }
                int segId = seg.getId();
                recordSeg.setSegId(segId);
                recordSegList.add(recordSeg);

                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        redisUtil.set("seg_" + word, segId);
                    }
                });
                thread.start();

            }
        }
        return recordSegService.addBatch(recordSegList);
    }

    @Override
    public int getMaxId() {
        return segmentationDao.getMaxId();
    }

    @Override
    public boolean insertBatchSeg(List<String> segs) {
        segmentationDao.insertBatchSeg(segs);
        return true;
    }

    @Override
    public List<String> getAllByWords(String word) {
        List<Segmentation> segmentation = segmentationDao.getAllByWords(word + "%");
        List<String> newList = new ArrayList<>();
        System.out.println(segmentation.size());
        if (segmentation.size() >= 8) {
            for (int i = 0; i < 8; i++) {
                Random random = new Random();
                int index = random.nextInt(segmentation.size());
                newList.add(segmentation.get(index).getWord());
                segmentation.remove(index);
            }
        } else {
            for (Segmentation segmentation1 : segmentation) {
                newList.add(segmentation1.getWord());
            }
        }
        return newList;
    }

    @Override
    public void saveData(String url, String caption) {
        // 1、接受py传过来的数据并保存数据库
        segmentationDao.saveData(url, caption);
        log.info("python接收的数据以保存至数据库");
        // 2、清空segmentation表数据并删除0-99表
        segmentationDao.truncateTable();
        log.info("成功清除分词表");
        for (int i = 0; i < 100; i++) {
            segmentationDao.deleteTable("data_seg_relation_" + i);
        }
        log.info("成功删除关系表");
        // 3、调用test中的两个方法
        addAllSegService.addSegs();
        log.info("分词完成");
        addAllSegService.addAllSegUseSplit();
        log.info("关联度计算完成");
    }

    @Override
    public Boolean addSeg(String word, Integer dataId) {
        //        Segmentation segmentation = new Segmentation();
        Segmentation seg = segmentationDao.selectOneSeg(word);
        if (seg == null) {
            //分词不存在 加入分词表
            segmentationDao.insertSeg(word);
        }


        //加入关系表
        RecordSeg recordSeg = new RecordSeg();
        recordSeg.setSegId(seg.getId());
        recordSeg.setDataId(dataId);
        RecordSeg rs = recordSegDao.selectOneRecordSeg(dataId, seg.getId());
        if (rs == null) {
            recordSeg.setCount(1);
            recordSegDao.insertRecordSeg(recordSeg);
        } else {
            int count = rs.getCount();
            //文中出现次数>1
            recordSeg.setCount(++count);
            recordSegDao.updateRecordSeg(recordSeg);
        }

        return true;

    }


}
