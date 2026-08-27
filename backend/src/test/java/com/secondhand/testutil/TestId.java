package com.secondhand.testutil;

import java.lang.reflect.Field;

/** 测试工具:JPA 实体的 id 由数据库生成、无 setter,单测中通过反射注入 */
public final class TestId {

    private TestId() {
    }

    public static void set(Object entity, Long id) {
        try {
            Field f = entity.getClass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(entity, id);
        } catch (Exception ignored) {
        }
    }
}
