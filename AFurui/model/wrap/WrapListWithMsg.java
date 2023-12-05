package com.magicair.webpj.AFurui.model.wrap;

import java.io.Serializable;
import java.util.List;

public class WrapListWithMsg<T>  implements Serializable {

    private String errMsg;//解析错误信息
    private List<T> listData;

    public String getErrMsg() {
        return errMsg;
    }

    public void setErrMsg(String errMsg) {
        this.errMsg = errMsg;
    }

    public List<T> getListData() {
        return listData;
    }

    public void setListData(List<T> listData) {
        this.listData = listData;
    }
}
