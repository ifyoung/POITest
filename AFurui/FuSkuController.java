package com.magicair.webpj.AFurui;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson2.JSONArray;
import com.magicair.webpj.core.Result;
import com.magicair.webpj.core.ResultGenerator;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.Objects;

import static com.magicair.webpj.AFurui.ConstantFu.C_BaseBoxFloder;
import static com.magicair.webpj.AFurui.ConstantFu.C_BaseSkuFloder;
import static com.magicair.webpj.utils.CommonUtils.isExcel;

@RestController
@RequestMapping("/fuSku")
public class FuSkuController {


    @Resource
    SkuServiceImpl skuService;


    @Resource
    FuruiStockServiceImpl furuiStockService;

    //箱规
    @PostMapping("/uploadSkuProperty")
    public Result uploadSkuProperty(MultipartFile file, String exData) {
        if (file == null) {
            return ResultGenerator.genFailResult("NoData");
        }
        JSONObject exOb = JSON.parseObject(exData);
        String tk = exOb.getString("token");
        int fileCount = exOb.getInteger("fileCount");


        String fileName = file.getOriginalFilename();// 文件原名称
        if (isExcel(Objects.requireNonNull(fileName))) {
            //不用保存？上传直接解析就行？
//            if (fileName.contains("箱规")) {
//                try {
//                    String path = furuiStockService.saveToAssets(C_BaseSkuFloder, file.getInputStream(), fileName);
//                    if (path == null) {
//                        return ResultGenerator.genFailResult("箱规文件存储失败");
//                    } else {
//                        return ResultGenerator.genSuccessResult(path);
//                    }
//                } catch (IOException e) {
//                    e.printStackTrace();
//                    return ResultGenerator.genFailResult("箱规文件读取失败");
//                }
//            } else {
//                return ResultGenerator.genFailResult("文件名需包含【箱规】二字！");
//            }

            try {
                JSONArray list = skuService.upSkuToUni(file.getInputStream());
                return ResultGenerator.genSuccessResult(list);
            } catch (IOException e) {
//                e.printStackTrace();
                return ResultGenerator.genFailResult("文件流读取失败");
            }

        } else {
            return ResultGenerator.genFailResult("文件读取失败");
        }
    }

}
