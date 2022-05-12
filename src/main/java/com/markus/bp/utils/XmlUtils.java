package com.markus.bp.utils;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;

/**
 * @author: markus
 * @date: 2022/5/10 6:04 下午
 * @Description: Xml 序列化 反序列化工具
 * @Blog: http://markuszhang.com/
 */
public class XmlUtils {

    public static <T> String serialize(T obj) {
        // 将employee对象序列化为XML
        XStream xStream = new XStream(new DomDriver());
        // 设置employee类的别名
        xStream.alias(obj.getClass().getSimpleName(), obj.getClass());
        String personXml = xStream.toXML(obj);
        return personXml;
    }

    public static void deserialize(String xml, Object obj) {
        // 将employee对象序列化为XML
        XStream xStream = new XStream(new DomDriver());
        xStream.alias(obj.getClass().getSimpleName(), obj.getClass());
        xStream.fromXML(xml, obj);
    }

}
