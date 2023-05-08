package com.searchengine.service.impl;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.huaban.analysis.jieba.JiebaSegmenter;
import com.huaban.analysis.jieba.SegToken;
import com.searchengine.dao.TDao;
import com.searchengine.entity.Record;
import com.searchengine.entity.Segmentation;
import com.searchengine.entity.T;
import com.searchengine.service.AddAllSegService;
import com.searchengine.service.RecordService;
import com.searchengine.service.SegmentationService;
import com.searchengine.utils.jieba.keyword.Keyword;
import com.searchengine.utils.jieba.keyword.TFIDFAnalyzer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;


/**
 * 扫描data表把所有内容分词并加入分词库
 */
@Service
public class AddAllSegServiceImpl implements AddAllSegService {

    static HashSet<String> stopWordsSet;
    TFIDFAnalyzer tfidfAnalyzer = new TFIDFAnalyzer();
    JiebaSegmenter jiebaSegmenter = new JiebaSegmenter();
    @Autowired
    private RecordService recordService;
    @Autowired
    private SegmentationService segmentationService;
    @Autowired
    private TDao tDao;

    /**
     * @author: optimjie
     * @description: 先单纯的添加分词表，为关系表的建立做准备
     * @date: 2022-05-23 10:53
     */
    public void addSegs() {
        // List<Record> records = recordService.queryAllRecord();
        List<String> segs = new ArrayList<>();
        //创建符合条件的布隆过滤器,预期数据量10000，错误率0.0001
        BloomFilter<String> bf = BloomFilter.create(Funnels.stringFunnel(StandardCharsets.UTF_8), 10000000, 0.00001);
        if (stopWordsSet == null) {
            stopWordsSet = new HashSet<>();
            loadStopWords(stopWordsSet, this.getClass().getResourceAsStream("/jieba/stop_words.txt"));
        }
        //获得表中数据量
        int dataTableCount = recordService.selectRecordCount();
        int size = (dataTableCount - 1) / 10000 + 1;
        for (int loop = 0; loop < size; loop++) {
            //查10000条数据从第二个参数开始
            List<Record> records = recordService.selectPartialRecords(10000, Math.max(0, loop * 10000));
            if (loop % 10 == 0 && loop != 0) {  // 这里注意loop应该不等于起始值，不一定非是0，因为起始值会空的，先这样写着。
                tDao.insert1(segs);
                segs.clear();
            }
            for (int i = loop * 10000; i < (loop + 1) * 10000; i++) {
                if (i >= dataTableCount) {
                    break;
                }
                Record record = records.get(i % 10000);
                String caption = record.getCaption();
                //把data里面的caption数据拿出来分词
                //SEARCH是基本模式，而INDEX会将SEARCH模式结果中的长词再次拆分
                List<SegToken> segTokens = jiebaSegmenter.process(caption, JiebaSegmenter.SegMode.INDEX);
                for (SegToken segToken : segTokens) {
                    String word = segToken.word;
                    if (stopWordsSet.contains(word)) {
                        continue; // 判断是否是停用词
                    }
                    //查看布隆过滤器是否包含word
                    if (!bf.mightContain(word)) {
                        bf.put(word);
                        segs.add(word);
                    }
                }
            }
        }
        tDao.insert1(segs);
    }

    /**
     * @author: optimjie
     * @description: 分表按照segId的最后两位来分，这样可以保证每个表是比较均匀的。
     * 因为在关系表很大的时候，主要的瓶颈在于找到所有包含某一个segId的data再将所有的tf值加起来比较大小
     * @date: 2022-05-23 11:01
     */
    public void addAllSegUseSplit() {
        List<Segmentation> segmentations = segmentationService.queryAllSeg();
        Map<String, Integer> wordToId = new HashMap<>(1000000);
        for (Segmentation seg : segmentations) {
            wordToId.put(seg.getWord(), seg.getId());
        }
        if (stopWordsSet == null) {
            stopWordsSet = new HashSet<>();
            loadStopWords(stopWordsSet, this.getClass().getResourceAsStream("/jieba/stop_words.txt"));
        }
        Map<Integer, List<T>> mp = new HashMap<>(100000);

        int cnt = 0;
        //获取表中数据量
        int dataTableCount = recordService.selectRecordCount();
        int size = (dataTableCount - 1) / 10000 + 1;
        for (int loop = 0; loop < size; loop++) {
            List<Record> records = recordService.selectPartialRecords(10000, Math.max(0, loop * 10000));
            for (int i = loop * 10000; i < (loop + 1) * 10000; i++) {
                if (i >= dataTableCount) {
                    break;
                }
                Record record = records.get(i % 10000);
                String caption = record.getCaption();
                List<SegToken> segTokens = jiebaSegmenter.process(caption, JiebaSegmenter.SegMode.INDEX);
                List<Keyword> keywords = tfidfAnalyzer.analyze(caption, 5);
                Map<String, T> countMap = new HashMap<>();
                for (SegToken segToken : segTokens) {
                    String word = segToken.word;
                    if (stopWordsSet.contains(word)) {
                        continue;  // 判断是否是停用词
                    }
                    int segId = wordToId.get(word);
                    int dataId = record.getId();
                    double tf = 0;
                    for (Keyword v : keywords) {
                        if (v.getName().equals(word)) {
                            tf = v.getTfidfvalue();
                            break;
                        }
                    }
                    if (!countMap.containsKey(word)) {
                        int count = 1;
                        countMap.put(word, new T(dataId, segId, tf, count));
                    } else {
                        T t = countMap.get(word);
                        int count = t.getCount();
                        t.setCount(++count);
                        countMap.put(word, t);
                    }
                }
                for (T t : countMap.values()) {
                    int segId = t.getSegId();
                    int idx = segId % 100;
                    List list = mp.getOrDefault(idx, new ArrayList<>(10000));
                    list.add(t);
                    mp.put(idx, list);
                    cnt++;
                }
                if (cnt > 100000) {  // 之所以这么搞，是因为在最后直接insert的话，会爆堆空间，虽然我已经开了4个G但好像还是不行。
                    cnt = 0;
                    for (Integer idx : mp.keySet()) {
                        String tableName = "data_seg_relation_" + idx;
                        tDao.createNewTable(tableName);
                        tDao.insert2(mp.get(idx), tableName);
                    }
                    mp = new HashMap<>(100000);
                }

            }
        }
        if (cnt > 0) {
            for (Integer idx : mp.keySet()) {
                String tableName = "data_seg_relation_" + idx;
                tDao.createNewTable(tableName);
                tDao.insert2(mp.get(idx), tableName);
            }
        }
    }

    private void loadStopWords(Set<String> set, InputStream in) {
        BufferedReader bufr;
        try {
            bufr = new BufferedReader(new InputStreamReader(in));
            String line = null;
            while ((line = bufr.readLine()) != null) {
                set.add(line.trim());
            }
            try {
                bufr.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
