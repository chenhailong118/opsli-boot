/**
 * Copyright 2020 OPSLI 快速开发平台 https://www.opsli.com
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.opsli.core.cache.local;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.XmlUtil;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.opsli.common.constants.CacheConstants;
import org.opsli.common.enums.CacheType;
import org.opsli.core.autoconfigure.properties.CacheProperties;
import org.opsli.core.msg.CoreMsg;
import org.opsli.core.utils.ThrowExceptionUtil;
import org.opsli.plugins.cache.EhCachePlugin;
import org.opsli.plugins.redis.RedisPlugin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.opsli.common.constants.OrderConstants.UTIL_ORDER;

/**
 * 缓存工具类
 *
 * 一、控制key的生命周期，Redis不是垃圾桶（缓存不是垃圾桶）
 * 建议使用expire设置过期时间(条件允许可以打散过期时间，防止集中过期)，不过期的数据重点关注idletime。
 *
 * 二、【强制】：拒绝bigkey(防止网卡流量、慢查询)
 * string类型控制在10KB以内，hash、list、set、zset元素个数不要超过5000。
 *
 * @author Parker
 * @date 2020-09-16 16:20
 */
@Slf4j
@Order(UTIL_ORDER)
@Component
public class CacheUtil {

    /** 热点数据缓存时间 秒 (6小时)*/
    private static int TTL_HOT_DATA_TIME = 21600;
    /** 空缓存时间 秒 */
    private final static int TTL_NIL_DATA_TIME = 300;
    /** Redis插件 */
    private static RedisPlugin redisPlugin;
    /** EhCache插件 */
    private static EhCachePlugin ehCachePlugin;
    /** Json key */
    public static final String JSON_KEY = "data";
    /** 空状态 key 前缀 */
    private static final String NIL_FLAG_PREFIX = "nil";
    /** 空状态 生效阈值 */
    private final static long NIL_FLAG_THRESHOLD = 3;

    /** 热点数据前缀 */
    private static String PREFIX_NAME;

    /** 增加初始状态开关 防止异常使用 */
    private static boolean IS_INIT;

    static {
        try {
            // 读取配置信息
            CacheUtil.readPropertyXML();
        }catch (Exception ignored){}
    }

    /***
     * 获得缓存前缀
     * @return String
     */
    public static String getPrefixName() {
        // 判断 工具类是否初始化完成
        ThrowExceptionUtil.isThrowException(!IS_INIT,
                CoreMsg.OTHER_EXCEPTION_UTILS_INIT);

        return PREFIX_NAME;
    }

    // ========================= GET =========================

    /**
     * 获得 普通 缓存
     * @param key 键
     * @return Object
     */
    public static Object getTimed(final String key){
        // 判断 工具类是否初始化完成
        ThrowExceptionUtil.isThrowException(!IS_INIT,
                CoreMsg.OTHER_EXCEPTION_UTILS_INIT);

        // 转换数据泛型
        return CacheUtil.get(key, false, false);
    }

    /**
     * 获得 普通 缓存
     * @param key 键
     * @param isSaveLocal 是否保存到本地
     * @return Object
     */
    public static Object getTimed(final String key, final boolean isSaveLocal){
        // 判断 工具类是否初始化完成
        ThrowExceptionUtil.isThrowException(!IS_INIT,
                CoreMsg.OTHER_EXCEPTION_UTILS_INIT);

        // 转换数据泛型
        return CacheUtil.get(key, false, isSaveLocal);
    }

    /**
     * 获得 普通 缓存
     * @param vClass 泛型Class
     * @param key 键
     * @return <V> 泛型
     */
    public static <V> V getTimed(final Class<V> vClass, final String key){
        // 判断 工具类是否初始化完成
        ThrowExceptionUtil.isThrowException(!IS_INIT,
                CoreMsg.OTHER_EXCEPTION_UTILS_INIT);

        // 转换数据泛型
        return CacheUtil.get(vClass, key, false, false);
    }

    /**
     * 获得 普通 缓存
     * @param vClass 泛型Class
     * @param key 键
     * @param isSaveLocal 是否保存到本地
     * @return <V> 泛型
     */
    public static <V> V getTimed(final Class<V> vClass, final String key,
                                 final boolean isSaveLocal){
        // 判断 工具类是否初始化完成
        ThrowExceptionUtil.isThrowException(!IS_INIT,
                CoreMsg.OTHER_EXCEPTION_UTILS_INIT);

        // 转换数据泛型
        return CacheUtil.get(vClass, key, false, isSaveLocal);
    }

    /**
     * 获得 普通 缓存
     * @param key 键
     * @return Object
     */
    public static Object getEden(final String key){
        // 判断 工具类是否初始化完成
        ThrowExceptionUtil.isThrowException(!IS_INIT,
                CoreMsg.OTHER_EXCEPTION_UTILS_INIT);

        // 转换数据泛型
        return CacheUtil.get(key, true, false);
    }

    /**
     * 获得 普通 缓存
     * @param key 键
     * @param isSaveLocal 是否保存到本地
     * @return Object
     */
    public static Object getEden(final String key, final boolean isSaveLocal){
        // 判断 工具类是否初始化完成
        ThrowExceptionUtil.isThrowException(!IS_INIT,
                CoreMsg.OTHER_EXCEPTION_UTILS_INIT);

        // 转换数据泛型
        return CacheUtil.get(key, true, isSaveLocal);
    }

    /**
     * 获得 普通 缓存
     * @param vClass 泛型Class
     * @param key 键
     * @return <V> 泛型
     */
    public static <V> V getEden(final String key, final Class<V> vClass){
        // 判断 工具类是否初始化完成
        ThrowExceptionUtil.isThrowException(!IS_INIT,
                CoreMsg.OTHER_EXCEPTION_UTILS_INIT);

        // 转换数据泛型
        return CacheUtil.get(vClass, key, true, false);
    }

    /**
     * 获得 普通 缓存
     * @param vClass 泛型Class
     * @param key 键
     * @param isSaveLocal 是否保存到本地
     * @return <V> 泛型
     */
    public static <V> V getEden(final String key, final boolean isSaveLocal,
                                final Class<V> vClass){
        // 判断 工具类是否初始化完成
        ThrowExceptionUtil.isThrowException(!IS_INIT,
                CoreMsg.OTHER_EXCEPTION_UTILS_INIT);

        // 转换数据泛型
        return CacheUtil.get(vClass, key, true, isSaveLocal);
    }

    /**
     * 获得 普通 缓存
     * @param vClass 泛型Class
     * @param key 键
     * @param isEden 是否永久层数据
     * @param isSaveLocal 是否保存到本地
     * @return <V> 泛型
     */
    private static <V> V get(final Class<V> vClass, final String key, final boolean isEden,
                             final boolean isSaveLocal){
        // 判断 工具类是否初始化完成
        ThrowExceptionUtil.isThrowException(!IS_INIT,
                CoreMsg.OTHER_EXCEPTION_UTILS_INIT);

        // 获得缓存数据
        Object cacheObj = CacheUtil.get(key, isEden, isSaveLocal);
        // 转换数据泛型
        return Convert.convert(vClass, cacheObj);
    }

    /**
     * 获得 普通 缓存
     * @param key 键
     * @param isEden 是否永久层数据
     * @param isSaveLocal 是否保存到本地
     * @return Object
     */
    private static Object get(final String key, final boolean isEden, final boolean isSaveLocal){
        // 判断 工具类是否初始化完成
        ThrowExceptionUtil.isThrowException(!IS_INIT,
                CoreMsg.OTHER_EXCEPTION_UTILS_INIT);

        try {
            // 缓存 Key
            String cacheKey  = CacheUtil.handleUsualKey(key, isEden);

            // 获得缓存Json
            JSONObject cacheJson;

            // 判读是否需要 先从本地缓存获取
            if(isSaveLocal){
                // 获得缓存Json
                cacheJson = ehCachePlugin.get(CacheConstants.EHCACHE_SPACE,
                        cacheKey, JSONObject.class);
                if(cacheJson != null){
                    return cacheJson.get(JSON_KEY);
                }
            }

            // 如果本地缓存找不到该缓存 则去远端缓存拉去缓存
            cacheJson = (JSONObject) redisPlugin.get(cacheKey);
            if(cacheJson != null){
                // 判读是否需要 存入本地EhCache
                if(isSaveLocal){
                    //存入EhCache
                    ehCachePlugin.put(CacheConstants.EHCACHE_SPACE,
                            cacheKey, cacheJson);
                }
            }

            return cacheJson != null ? cacheJson.get(JSON_KEY) : null;
        }catch (Exception e){
            log.error(e.getMessage(),e);
        }
        return null;
    }



    /**
     * 获得 Hash 缓存
     * @param vClass 泛型Class
     * @param key 键
     * @param field 字段名
     * @return <V> 泛型
     */
    public static <V> V getHash(final Class<V> vClass, final String key, final String field){
        // 判断 工具类是否初始化完成
        ThrowExceptionUtil.isThrowException(!IS_INIT,
                CoreMsg.OTHER_EXCEPTION_UTILS_INIT);

        // 获得缓存数据
        Object cacheObj = CacheUtil.getHash(key, field, false);
        // 转换数据泛型
        return Convert.convert(vClass, cacheObj);
    }

    /**
     * 获得 Hash 缓存
     * @param key 键
     * @param field 字段名
     * @param isSaveLocal 是否保存到本地
     * @param vClass 泛型Class
     * @return <V> 泛型
     */
    public static <V> V getHash(final Class<V> vClass, final String key,
                                final String field, final boolean isSaveLocal){
        // 判断 工具类是否初始化完成
        ThrowExceptionUtil.isThrowException(!IS_INIT,
                CoreMsg.OTHER_EXCEPTION_UTILS_INIT);

        // 获得缓存数据
        Object cacheObj = CacheUtil.getHash(key, field, isSaveLocal);
        // 转换数据泛型
        return Convert.convert(vClass, cacheObj);
    }

    /**
     * 获得 Hash 缓存
     * @param key 键
     * @param field 字段名
     * @return Object
     */
    public static Object getHash(final String key, final String field){
        // 判断 工具类是否初始化完成
        ThrowExceptionUtil.isThrowException(!IS_INIT,
                CoreMsg.OTHER_EXCEPTION_UTILS_INIT);

        return CacheUtil.getHash(key, field, false);
    }

    /**
     * 获得 Hash 缓存
     * @param key 键
     * @param field 字段名
     * @param isSaveLocal 是否保存到本地
     * @return Object
     */
    public static Object getHash(final String key, final String field,
                                 final boolean isSaveLocal){
        // 判断 工具类是否初始化完成
        ThrowExceptionUtil.isThrowException(!IS_INIT,
                CoreMsg.OTHER_EXCEPTION_UTILS_INIT);

        try {
            // 缓存 Key
            String cacheKey  = CacheUtil.handleKey(CacheType.EDEN_HASH, key);

            // 获得缓存Json
            JSONObject cacheJson;

            // 判读是否需要 先从本地缓存获取
            if(isSaveLocal){
                // 获得缓存Json
                cacheJson = ehCachePlugin.get(CacheConstants.EHCACHE_SPACE,
                        cacheKey +":"+ field, JSONObject.class);
                if(cacheJson != null){
                    return cacheJson.get(JSON_KEY);
                }
            }

            // 如果本地缓存找不到该缓存 则去远端缓存拉去缓存
            cacheJson = (JSONObject) redisPlugin.hGet(cacheKey, field);
            if(cacheJson != null){
                // 判读是否需要 存入本地EhCache
                if(isSaveLocal){
                    //存入EhCache
                    ehCachePlugin.put(CacheConstants.EHCACHE_SPACE,
                        cacheKey + ":" + field, cacheJson);
                }
            }

            return cacheJson != null ? cacheJson.get(JSON_KEY) : null;
        }catch (Exception e){
            log.error(e.getMessage(),e);
        }
        return null;
    }


    /**
     * 获得 Hash 缓存
     * @param key 键
     * @return Object
     */
    public static Map<String, Object> getHashAll(final String key){
        // 判断 工具类是否初始化完成
        ThrowExceptionUtil.isThrowException(!IS_INIT,
                CoreMsg.OTHER_EXCEPTION_UTILS_INIT);

        try {
            // 缓存 Key
            String cacheKey  = CacheUtil.handleKey(CacheType.EDEN_HASH, key);

            Map<String, Object> retMap = Maps.newHashMap();

            // 如果本地缓存找不到该缓存 则去远端缓存拉去缓存
            Map<Object, Object> allCache = redisPlugin.hGetAll(cacheKey);
            if(CollUtil.isEmpty(allCache)){
                return retMap;
            }

            for (Map.Entry<Object, Object> entry : allCache.entrySet()) {
                // 赋值
                JSONObject jsonObject = (JSONObject) entry.getValue();
                if (jsonObject == null) {
                    continue;
                }
                Object data = jsonObject.get(CacheUtil.JSON_KEY);
                if (data == null) {
                    continue;
                }

                retMap.put(Convert.toStr(entry.getKey()), data);
            }

            return retMap;
        }catch (Exception e){
            log.error(e.getMessage(),e);
        }
        return null;
    }

    // ========================= PUT =========================


    /**
     * 存普通缓存
     * @param key 键
     * @param value 值
     * @return boolean
     */
    public static boolean put(final String key, final Object value) {
        // 判断 工具类是否初始化完成
        ThrowExceptionUtil.isThrowException(!IS_INIT,
                CoreMsg.OTHER_EXCEPTION_UTILS_INIT);

        return CacheUtil.put(key, value, false);
    }

    /**
     * 存永久缓存
     * @param key 键
     * @param value 值
     * @param isEden 是否永久存储
     * @return boolean
     */
    public static boolean put(final String key, final Object value, final boolean isEden) {
        // 判断 工具类是否初始化完成
        ThrowExceptionUtil.isThrowException(!IS_INIT,
                CoreMsg.OTHER_EXCEPTION_UTILS_INIT);

        try {
            // 自动处理 key

            // 则统一转换为 JSONObject
            JSONObject cacheJson = new JSONObject();
            cacheJson.put(JSON_KEY, value);

            // 缓存 Key
            String cacheKey  = CacheUtil.handleUsualKey(key, isEden);

            // 判断是否为永久存储
            if(isEden) {
                // 存入Redis
                return redisPlugin.put(cacheKey, cacheJson);
            }else{
                // 随机缓存失效时间 防止缓存雪崩
                // 范围在当前时效的 1.2 - 2倍

                // 生成随机失效时间
                int timeout = RandomUtil.randomInt(
                        Convert.toInt(TTL_HOT_DATA_TIME * 1.2),
                        Convert.toInt(TTL_HOT_DATA_TIME * 2)
                );

                // 存入Redis
                return redisPlugin.put(cacheKey, cacheJson, timeout);
            }
        }catch (Exception e){
            log.error(e.getMessage(),e);
        }
        return false;
    }



    /**
     * 存 永久 Hash 缓存
     * @param key 键
     * @param field 字段名
     * @param value 值
     * @return boolean
     */
    public static boolean putHash(final String key, final String field, final Object value) {
        // 判断 工具类是否初始化完成
        ThrowExceptionUtil.isThrowException(!IS_INIT,
                CoreMsg.OTHER_EXCEPTION_UTILS_INIT);

        try {
            // 处理 key
            String cacheKey = CacheUtil.handleKey(CacheType.EDEN_HASH, key);

            // 则统一转换为 JSONObject
            JSONObject cacheJson = new JSONObject();
            cacheJson.put(JSON_KEY, value);

            // 存入Redis
            return redisPlugin.hPut(cacheKey, field, cacheJson);
        }catch (Exception e){
            log.error(e.getMessage(),e);
        }
        return false;
    }


    // ========================= DEL =========================


    /**
     * 删缓存
     * @param key 键
     * @return boolean
     */
    public static boolean del(final String key) {
        // 判断 工具类是否初始化完成
        ThrowExceptionUtil.isThrowException(!IS_INIT,
                CoreMsg.OTHER_EXCEPTION_UTILS_INIT);

        try {
            // 计数器
            int count = 0;

            Object timed = CacheUtil.getTimed(key);
            Object eden = CacheUtil.getEden(key);

            // 删除key 集合
            List<String> cacheKeys = Lists.newArrayList();
            if(timed != null){
                count+=2;
                // 处理 key - 时控数据
                cacheKeys.add(
                        CacheUtil.handleKey(CacheType.TIMED, key)
                );
            }
            if(eden != null){
                count+=2;
                // 处理 key - 永久数据
                cacheKeys.add(
                        CacheUtil.handleKey(CacheType.EDEN, key));
            }

            // 循环删除缓存数据
            for (String cacheKey : cacheKeys) {

                // 删除 EhCache
                boolean ehcacheRet = ehCachePlugin.delete(CacheConstants.EHCACHE_SPACE, cacheKey);
                if(ehcacheRet){
                    count--;
                }

                // 删除 Redis
                boolean redisRet = redisPlugin.del(cacheKey);
                if(redisRet){
                    count--;
                }
            }

            return count == 0;
        }catch (Exception e){
            log.error(e.getMessage(),e);
        }
        return false;
    }

    /**
     * 删 Hash 缓存
     * @param key 键
     * @param field 字段名
     * @return boolean
     */
    public static boolean delHash(final String key, final String field) {
        // 判断 工具类是否初始化完成
        ThrowExceptionUtil.isThrowException(!IS_INIT,
                CoreMsg.OTHER_EXCEPTION_UTILS_INIT);

        try {
            // 计数器
            int count = 2;

            // 自动处理 key
            String cacheKey = CacheUtil.handleKey(CacheType.EDEN_HASH, key);

            // 删除 EhCache
            boolean ehcacheRet = ehCachePlugin.delete(CacheConstants.EHCACHE_SPACE,cacheKey +":"+ field);
            if(ehcacheRet){
                count--;
            }

            // 删除 Redis
            Long hDeleteLong = redisPlugin.hDelete(cacheKey, field);
            if(hDeleteLong != null && hDeleteLong > 0){
                count--;
            }

            return count == 0;
        }catch (Exception e){
            log.error(e.getMessage(),e);
        }
        return false;
    }

    // ====================================================================

    /**
     *  放一个空属性 有效时间为 5分钟
     *  用于 防止穿透判断 弥补布隆过滤器
     *
     * @param key 键
     * @return boolean
     */
    public static boolean putNilFlag(String key) {
        // 判断 工具类是否初始化完成
        ThrowExceptionUtil.isThrowException(!IS_INIT,
                CoreMsg.OTHER_EXCEPTION_UTILS_INIT);
        // 处理缓存 key
        String cacheKey = CacheUtil.handleKey(NIL_FLAG_PREFIX + ":" + key);

        try {
            // 存入Redis
            Long increment = redisPlugin.increment(cacheKey);
            // 设置失效时间
            redisPlugin.expire(cacheKey, TTL_NIL_DATA_TIME);
            return increment != null;
        }catch (Exception e){
            log.error(e.getMessage(),e);
        }
        return false;
    }

    /**
     *  删除空属性
     *  用于 防止穿透判断 弥补布隆过滤器
     *
     * @param key 键
     * @return boolean
     */
    public static boolean delNilFlag(String key) {
        // 判断 工具类是否初始化完成
        ThrowExceptionUtil.isThrowException(!IS_INIT,
                CoreMsg.OTHER_EXCEPTION_UTILS_INIT);

        // 处理缓存 key
        String cacheKey = CacheUtil.handleKey(NIL_FLAG_PREFIX + ":" + key);
        try {
            // 删除Redis
            return redisPlugin.del(cacheKey);
        }catch (Exception e){
            log.error(e.getMessage(),e);
        }
        return false;
    }


    /**
     *  获得一个空属性 有效时间为 5分钟
     *  用于 防止穿透判断 弥补布隆过滤器
     *
     * @param key 键
     * @return boolean
     */
    public static boolean hasNilFlag(String key) {
        // 判断 工具类是否初始化完成
        ThrowExceptionUtil.isThrowException(!IS_INIT,
                CoreMsg.OTHER_EXCEPTION_UTILS_INIT);
        // 处理缓存 key
        String cacheKey = CacheUtil.handleKey(NIL_FLAG_PREFIX + ":" + key);

        try {
            Object nilObj = redisPlugin.get(cacheKey);
            if(nilObj == null){
                return false;
            }

            Long nilNum = Convert.toLong(nilObj, 0L);
            return NIL_FLAG_THRESHOLD < nilNum;
        }catch (Exception e){
            log.error(e.getMessage(),e);
        }
        return false;
    }


    // ====================================================================

    /**
     * 处理 key 默认为临时
     * @param key 缓存Key
     * @return String
     */
    public static String handleKey(String key){
        // 判断 工具类是否初始化完成
        ThrowExceptionUtil.isThrowException(!IS_INIT,
                CoreMsg.OTHER_EXCEPTION_UTILS_INIT);

        return CacheUtil.handleKey(CacheType.TIMED, key);
    }

    /**
     * 处理 key
     * @param cacheType 缓存类型
     * @param key 缓存Key
     * @return String
     */
    public static String handleKey(CacheType cacheType, String key){
        // 判断 工具类是否初始化完成
        ThrowExceptionUtil.isThrowException(!IS_INIT,
                CoreMsg.OTHER_EXCEPTION_UTILS_INIT);

        return PREFIX_NAME + cacheType.getName() + ":" +
                key;
    }

    /**
     * 内部处理 普通 key
     * @param key 缓存Key
     * @param isEden 是否永久
     * @return String
     */
    private static String handleUsualKey(String key, boolean isEden){
        if(isEden){
            return CacheUtil.handleKey(CacheType.EDEN, key);
        }
        return CacheUtil.handleKey(CacheType.TIMED, key);
    }

    /**
     * 读配置文件
     */
    private static void readPropertyXML() throws IOException {
        // 有坑 读 xml
        ClassPathResource resource = new ClassPathResource("config/ehcache-opsli.xml");
        Document document = XmlUtil.readXML(resource.getInputStream());
        NodeList nodeList = document.getElementsByTagName("cache");
        if(nodeList != null){
            for (int i = 0; i < nodeList.getLength(); i++) {
                Node item = nodeList.item(i);
                NamedNodeMap attributes = item.getAttributes();
                if(attributes == null){
                    continue;
                }
                Node alias = attributes.getNamedItem("alias");
                if("hotData".equals(alias.getNodeValue())){
                    NodeList childNodes = item.getChildNodes();
                    if(childNodes != null){
                        for (int j = 0; j < childNodes.getLength(); j++) {
                            if("expiry".equals(childNodes.item(j).getNodeName())){
                                NodeList expiryNodes = childNodes.item(j).getChildNodes();
                                if(expiryNodes != null){
                                    for (int k = 0; k < expiryNodes.getLength(); k++) {
                                        if("ttl".equals(expiryNodes.item(k).getNodeName())){
                                            Node ttlNode = expiryNodes.item(k);
                                            Node ttlValue = ttlNode.getFirstChild();
                                            // 默认 60000秒 6小时
                                            TTL_HOT_DATA_TIME = Convert.toInt(ttlValue.getNodeValue(), 21600);
                                            break;
                                        }
                                    }
                                }
                                break;
                            }
                        }
                    }
                    break;
                }
            }
        }
    }

    /**
     * 初始化
     */
    @Autowired
    public void init(CacheProperties cacheProperties,
                     RedisPlugin redisPlugin,
                     EhCachePlugin ehCachePlugin){

        CacheUtil.PREFIX_NAME = Convert.toStr(cacheProperties.getPrefix(), "opsli") + ":";
        CacheUtil.redisPlugin = redisPlugin;
        CacheUtil.ehCachePlugin = ehCachePlugin;

        IS_INIT = true;
    }

}
