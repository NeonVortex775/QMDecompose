package com.qingming.decompose.util;

import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/** NBT through NBTAPI reflection, so NBTAPI never becomes a hard dependency. */
public final class NbtUtil {

    private static Logger log;
    private static boolean ready = false;
    private static boolean broken = false;

    private static Constructor<?> mItemCtor;
    private static Method mGetCompound;
    private static Method mGetItem;

    private NbtUtil() {
    }

    public static void init(Logger logger) {
        log = logger;
    }

    private static boolean prepare() {
        if (broken) {
            return false;
        }
        if (ready) {
            return true;
        }
        try {
            Class<?> c = Class.forName("de.tr7zw.nbtapi.NBTItem");
            mItemCtor = c.getConstructor(ItemStack.class);
            mGetCompound = c.getMethod("getCompound");
            mGetItem = c.getMethod("getItem");
            ready = true;
            return true;
        } catch (Throwable t) {
            broken = true;
            if (log != null) {
                log.log(Level.WARNING, "[NBT] 未找到 NBTAPI，nbt 匹配与 nbt 写入已停用", t);
            }
            return false;
        }
    }

    public static boolean available() {
        return prepare();
    }

    public static String read(ItemStack item, String path) {
        if (item == null || path == null || !prepare()) {
            return null;
        }
        try {
            Object nbtItem = mItemCtor.newInstance(item);
            Object compound = mGetCompound.invoke(nbtItem);
            if (compound == null) {
                return null;
            }
            String[] segs = path.split("\\.");
            Object cur = compound;
            for (int i = 0; i < segs.length; i++) {
                String seg = segs[i];
                Class<?> cc = cur.getClass();
                if (!((Boolean) cc.getMethod("hasKey", String.class).invoke(cur, seg))) {
                    return null;
                }
                if (i == segs.length - 1) {
                    return value(cc, cur, seg);
                }
                cur = cc.getMethod("getCompound", String.class).invoke(cur, seg);
                if (cur == null) {
                    return null;
                }
            }
            return null;
        } catch (Throwable t) {
            broken = true;
            if (log != null) {
                log.log(Level.WARNING, "[NBT] 读取失败，已停用 NBT 支持", t);
            }
            return null;
        }
    }

    private static String value(Class<?> cc, Object compound, String key) throws Exception {
        Object type = cc.getMethod("getType", String.class).invoke(compound, key);
        String tn = type == null ? "" : type.toString().toUpperCase();
        if (tn.contains("STRING")) {
            return (String) cc.getMethod("getString", String.class).invoke(compound, key);
        }
        if (tn.contains("INT")) {
            return String.valueOf(cc.getMethod("getInteger", String.class).invoke(compound, key));
        }
        if (tn.contains("LONG")) {
            return String.valueOf(cc.getMethod("getLong", String.class).invoke(compound, key));
        }
        if (tn.contains("DOUBLE")) {
            return String.valueOf(cc.getMethod("getDouble", String.class).invoke(compound, key));
        }
        if (tn.contains("FLOAT")) {
            return String.valueOf(cc.getMethod("getFloat", String.class).invoke(compound, key));
        }
        if (tn.contains("SHORT")) {
            return String.valueOf(cc.getMethod("getShort", String.class).invoke(compound, key));
        }
        if (tn.contains("BYTE")) {
            return String.valueOf(cc.getMethod("getByte", String.class).invoke(compound, key));
        }
        if (tn.contains("BOOLEAN")) {
            return String.valueOf(cc.getMethod("getBoolean", String.class).invoke(compound, key));
        }
        if (tn.contains("UUID")) {
            return String.valueOf(cc.getMethod("getUUID", String.class).invoke(compound, key));
        }
        Object raw = cc.getMethod("get", String.class).invoke(compound, key);
        return raw == null ? null : String.valueOf(raw);
    }

    public static ItemStack apply(ItemStack item, Map<String, String> values) {
        if (item == null || values == null || values.isEmpty() || !prepare()) {
            return item;
        }
        try {
            Object nbtItem = mItemCtor.newInstance(item);
            Object root = mGetCompound.invoke(nbtItem);
            Class<?> cc = root.getClass();
            for (Map.Entry<String, String> e : values.entrySet()) {
                String key = e.getKey();
                String val = e.getValue() == null ? "" : e.getValue();
                Object target = root;
                int dot = key.lastIndexOf('.');
                if (dot > 0) {
                    target = cc.getMethod("getOrCreateCompound", String.class).invoke(root, key.substring(0, dot));
                    key = key.substring(dot + 1);
                }
                if (target == null) {
                    continue;
                }
                if (val.matches("-?\\d{1,9}")) {
                    target.getClass()
                            .getMethod("setInteger", String.class, Integer.class)
                            .invoke(target, key, Integer.valueOf(val));
                } else {
                    target.getClass()
                            .getMethod("setString", String.class, String.class)
                            .invoke(target, key, val);
                }
            }
            Object result = mGetItem.invoke(nbtItem);
            return result instanceof ItemStack ? (ItemStack) result : item;
        } catch (Throwable t) {
            broken = true;
            if (log != null) {
                log.log(Level.WARNING, "[NBT] 写入失败，已停用 NBT 支持", t);
            }
            return item;
        }
    }
}
