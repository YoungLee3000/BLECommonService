package com.nlscan.blecommservice.utils;

/**
 *
 @SCNMOD0   触发扫码模式                           40 53 43 4E 4D 4F 44 30
 @SCNMOD3   连续扫码模式                           40 53 43 4E 4D 4F 44 33
 @GRBENA0   关闭解码成功提示音                     40 47 52 42 45 4E 41 30
 @GRBENA1   开启解码成功提示音                     40 47 52 42 45 4E 41 31
 @GRVENA0   关闭解码成功震动                       40 47 52 56 45 4E 41 30
 @GRVENA1   开启解码成功震动                       40 47 52 56 45 4E 41 31
 */
public class Command {
    //查找工牌
    public static final int CMD_FIND_BADGE = 0x01;
    //触发一次电量回调
    public static final int CMD_GET_BATTERY_LEVEL = 0x02;

   /* //开启解码成功声音
    public static final int CMD_ENABLE_BADGE_VOICE = 0x03;
    //关闭解码成功声音
    public static final int CMD_DISABLE_BADGE_VOICE = 0x04;
    //开启解码成功震动
    public static final int CMD_ENABLE_BADGE_SHOCK = 0x05;
    //关闭解码成功震动
    public static final int CMD_DISABLE_BADGE_SHOCK = 0x06;
    //连扫模式
    public static final int CMD_BADGE_SCAN_CONTINUE = 0x07;
    //触发扫码
    public static final int CMD_BADGE_SCAN_TRIGGER = 0x08;*/


}
