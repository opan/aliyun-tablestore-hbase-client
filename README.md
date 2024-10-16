# Aliyun Tablestore HBase client for Java

[![Software License](https://img.shields.io/badge/license-apache2-brightgreen.svg)](LICENSE)
[![GitHub version](https://badge.fury.io/gh/aliyun%2Faliyun-tablestore-hbase-client.svg)](https://badge.fury.io/gh/aliyun%2Faliyun-tablestore-hbase-client)

[表格存储](https://www.aliyun.com/product/ots/)是阿里云主打的NoSQL大数据分布式数据库，目前已经大规模服务于众多阿里巴巴集团内部和外部应用，包括邮箱，钉钉，菜鸟，搜索，交易，推荐等。

表格存储的设计目标就是处理海量数据，持续大并发和低延迟，特别适用于金融，用户分析和物联网。

表格存储可以动态扩容或缩容P级的存储空间，每秒可处理上亿的请求，支持热升级，升级过程中也不会影响用户的访问。

目前，访问表格存储可以有多种形式：

- 表格存储SDK，目前支持Java，C++，Php，Python和C#（推荐）
- Tablestore HBase client

如果要使用Tablestore HBase client，可参考下列文档

- [表格存储和HBase API的区别](https://help.aliyun.com/document_detail/501220.html)
- [如何使用Tablestore HBase client](https://help.aliyun.com/document_detail/50127.html)
- [迁移较早版本的HBase](https://help.aliyun.com/document_detail/50166.html)
- [示例：HelloWorld](https://help.aliyun.com/document_detail/50163.html)


## Compile

```
# skip compile and install test
mvn clean install -Dmaven.test.skip=true
```

## Test
set your config in src/test/resources/hbase-site.xml

```xml
<?xml version="1.0"?>
<?xml-stylesheet type="text/xsl" href="configuration.xsl"?>
<!--
/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
-->
<configuration>
    <property>
        <name>hbase.client.connection.impl</name>
        <value>com.alicloud.tablestore.hbase.TablestoreConnection</value>
    </property>
    <property>
        <name>tablestore.client.endpoint</name>
        <value>http://xxx:80</value>
    </property>
    <property>
        <name>tablestore.client.instancename</name>
        <value>xxx</value>
    </property>
    <property>
        <name>tablestore.client.accesskeyid</name>
        <value>xxx</value>
    </property>
    <property>
        <name>tablestore.client.accesskeysecret</name>
        <value>xxx</value>
    </property>
    <property>
        <name>hbase.client.tablestore.family</name>
        <value>s</value>
    </property>
    <property>
        <name>hbase.client.tablestore.table</name>
        <value>ots_adaptor</value>
    </property>
    <property>
        <name>hbase.defaults.for.version.skip</name>
        <value>true</value>
    </property>
    <property>
        <name>hbase.hconnection.meta.lookup.threads.core</name>
        <value>4</value>
    </property>
    <property>
        <name>hbase.hconnection.threads.keepalivetime</name>
        <value>3</value>
    </property>
</configuration>
```
