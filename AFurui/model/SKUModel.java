package com.magicair.webpj.AFurui.model;

public class SKUModel {

    //站点 店铺 款式 颜色 尺码
   //ZD  DP   KS   YS  CM

    //MD5 ASCII AES？
    private String UUID;//自建唯一标志，相同内容进来应该相同且唯一！

    private String group;

    private String name;//中文名
    private String code;//代码，固定两位大写字母
    private String extra;//额外标签

    private String oldCode;//老代号,对应老的code


    private String note;//备注

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getExtra() {
        return extra;
    }

    public void setExtra(String extra) {
        this.extra = extra;
    }

    public String getOldCode() {
        return oldCode;
    }

    public void setOldCode(String oldCode) {
        this.oldCode = oldCode;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
