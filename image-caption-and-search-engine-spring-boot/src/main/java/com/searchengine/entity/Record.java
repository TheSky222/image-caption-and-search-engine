package com.searchengine.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@TableName("data")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Record {

    private Integer id;

    private String url;

    private String caption;
}
