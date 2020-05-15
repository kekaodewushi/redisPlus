# redisPlus

##基本介绍
是一个基于redis的连接工具，类似<RedisDesktopManager>,
优势是做了循环处理，防止数据过大时造成内存溢出，加载目录与加载数据分开来，在提升效率时不影响使用

## 连接设置
redisPlus-data/conf目录下的 connect.json文件
host地址，集群请使用逗号隔开
name名字
pass密码
多个环境使用多个json对象拼接，如下
 {
    "hosts": "10.0.0.1,10.0.0.2,10.0.0.3",
    "name": "测试",
    "pass": "12jklakspij&#@$bo"
  },
  {
    "hosts": "10.1.1.0,10.1.1.1:7001,10.1.1.2:7002",
    "name": "开发",
    "pass": "12jklakspij&#@$bo"
  }