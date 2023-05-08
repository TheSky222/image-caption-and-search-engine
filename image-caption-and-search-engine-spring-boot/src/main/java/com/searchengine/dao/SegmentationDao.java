package com.searchengine.dao;

import com.searchengine.entity.Segmentation;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SegmentationDao {
    //查看所有分词
    List<Segmentation> selectAllSeg();
    //加入新分词
    int insertSeg(String word);
    //查询单个分词对应的id
    Segmentation selectOneSeg(String word);
    //根据id查询
    Segmentation selectOneById(@Param("id") int id);
    //查询最大id
    int getMaxId();
    //批量插入分词
    boolean insertBatchSeg(@Param("segs")List<String> segs);

    List<Segmentation> getAllByWords(String word);

    void saveData(@org.apache.ibatis.annotations.Param("url") String url,@org.apache.ibatis.annotations.Param("caption") String caption);

    void truncateTable();
    @Update("DROP TABLE IF EXISTS ${tableName}")
    void deleteTable(@org.apache.ibatis.annotations.Param("tableName") String tableName);
}
