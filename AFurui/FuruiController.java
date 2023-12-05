package com.magicair.webpj.AFurui;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.magicair.webpj.AFurui.model.BoxRules;
import com.magicair.webpj.AFurui.model.CargoInfo;
import com.magicair.webpj.AFurui.model.DepotRes;
import com.magicair.webpj.AFurui.model.ShenBaoInfo;
import com.magicair.webpj.AFurui.model.wrap.WrapListWithMsg;
import com.magicair.webpj.core.Result;
import com.magicair.webpj.core.ResultGenerator;
import com.magicair.webpj.utils.CommonUtils;
import com.magicair.webpj.utils.Lg;
import com.magicair.webpj.utils.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static com.magicair.webpj.AFurui.ConstantFu.*;
import static com.magicair.webpj.AFurui.FuruiStockServiceImpl.G_inUploadService;
import static com.magicair.webpj.AFurui.FuruiStockServiceImpl.G_inUploadServiceNames;
import static com.magicair.webpj.utils.CommonUtils.isCsv;
import static com.magicair.webpj.utils.CommonUtils.isExcel;

@RestController
@RequestMapping("/functionUtills")
public class FuruiController {

    @Value("${web.upload-path}")
    private String realPath;
    @Resource
    FuruiStockServiceImpl furuiStockService;
    @Resource
    FuruiBeihuoServiceImpl furuiBeihuoService;

    @Resource
    FuruiAMZcsvServiceImpl furuiAMZcsvService;
    @Resource
    FuruiAMZcsvFeeServiceImpl furuiAMZcsvFeeService;

    @Resource
    FuruiAMZcsvTrustDeedServiceImpl furuiAMZcsvTrustDeedService;


    @Resource
    private FuruiSKUPutInBoxesImpl furuiSKUPutInBoxes;


    @Value("${web.excel-path}")
    private String realPathExcel;

    //箱规
    @PostMapping("/uploadBox")
    public Result uploadBox(MultipartFile file, String exData) {
        if (file == null) {
            return ResultGenerator.genFailResult("NoData");
        }
        JSONObject exOb = JSON.parseObject(exData);
        String tk = exOb.getString("token");
        int fileCount = exOb.getInteger("fileCount");


        String fileName = file.getOriginalFilename();// 文件原名称
        if (isExcel(Objects.requireNonNull(fileName))) {
            if (fileName.contains("箱规")) {
                try {
                    String path = furuiStockService.saveToAssets(C_BaseBoxFloder, file.getInputStream(), fileName);
                    if (path == null) {
                        return ResultGenerator.genFailResult("箱规文件存储失败");
                    } else {
                        return ResultGenerator.genSuccessResult(path);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    return ResultGenerator.genFailResult("箱规文件读取失败");
                }
            } else {
                return ResultGenerator.genFailResult("文件名需包含【箱规】二字！");
            }

        } else {
            return ResultGenerator.genFailResult("箱规文件名读取失败");
        }
    }

    //仓库
    @PostMapping("/uploadStock")
    public Result uploadStock(MultipartFile file, String exData) {
        if (file == null) {
            return ResultGenerator.genFailResult("NoData");
        }
        JSONObject exOb = JSON.parseObject(exData);
        String tk = exOb.getString("token");
        int fileCount = exOb.getInteger("fileCount");


        String fileName = file.getOriginalFilename();// 文件原名称
        if (isExcel(Objects.requireNonNull(fileName))) {
            if (fileName.contains("仓库")) {
                try {
                    String path = furuiStockService.saveToAssets(C_BaseStockInfoFloder, file.getInputStream(), fileName);
                    if (path == null) {
                        return ResultGenerator.genFailResult("仓库文件存储失败");
                    } else {
                        return ResultGenerator.genSuccessResult(path);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    return ResultGenerator.genFailResult("仓库文件读取失败");
                }
            } else {
                return ResultGenerator.genFailResult("文件名需包含【仓库】二字！");
            }

        } else {
            return ResultGenerator.genFailResult("仓库文件名读取失败");
        }
    }

    //申报要素
    @PostMapping("/uploadShenbao")
    public Result uploadShenbao(MultipartFile file, String exData) {
        if (file == null) {
            return ResultGenerator.genFailResult("NoData");
        }
        JSONObject exOb = JSON.parseObject(exData);
        String tk = exOb.getString("token");
        int fileCount = exOb.getInteger("fileCount");


        String fileName = file.getOriginalFilename();// 文件原名称
        if (isExcel(Objects.requireNonNull(fileName))) {
            if (fileName.contains("申报要素")) {
                try {
                    String path = furuiStockService.saveToAssets(C_BaseShenbaoInfoFloder, file.getInputStream(), fileName);
                    if (path == null) {
                        return ResultGenerator.genFailResult("申报要素文件存储失败");
                    } else {
                        return ResultGenerator.genSuccessResult(path);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    return ResultGenerator.genFailResult("申报要素文件读取失败");
                }
            } else {
                return ResultGenerator.genFailResult("文件名需包含【申报要素】！");
            }

        } else {
            return ResultGenerator.genFailResult("申报要素文件名读取失败");
        }
    }

    //获取箱规接口
    @PostMapping("/getBoxList")
    public Result getBoxList(@RequestParam(defaultValue = "1") Integer num) {

        File[] rules = furuiStockService.getLatestBoxRules(null);
        if (rules == null) {

            return ResultGenerator.genFailResult("【箱规】文件缺失");
        } else {
//            String path = rules[0].getPath();
            List<String> paths = new ArrayList<>();
            for (File rule : rules) {
                paths.add(C_BaseBoxFloder + "/" + rule.getName());
            }
            return ResultGenerator.genSuccessResult(paths);
        }
    }

    //获取仓库信息接口
    @PostMapping("/getStockInfoList")
    public Result getStockInfoList(@RequestParam(defaultValue = "1") Integer num) {

        File[] rules = furuiStockService.getLatestBoxRules(C_BaseStockInfoFloder);
        if (rules == null) {

            return ResultGenerator.genFailResult("【仓库信息】文件缺失");
        } else {
//            String path = rules[0].getPath();
            List<String> paths = new ArrayList<>();
            for (File rule : rules) {
                paths.add(C_BaseStockInfoFloder + "/" + rule.getName());
            }
            return ResultGenerator.genSuccessResult(paths);
        }
    }

    //获取申报要素
    @PostMapping("/getShenbaoInfoList")
    public Result getShenbaoInfoList(@RequestParam(defaultValue = "1") Integer num) {

        File[] rules = furuiStockService.getLatestBoxRules(C_BaseShenbaoInfoFloder);
        if (rules == null) {

            return ResultGenerator.genFailResult("【申报要素】文件缺失");
        } else {
//            String path = rules[0].getPath();
            List<String> paths = new ArrayList<>();
            for (File rule : rules) {
                paths.add(C_BaseShenbaoInfoFloder + "/" + rule.getName());
            }
            return ResultGenerator.genSuccessResult(paths);
        }
    }


    //前端批量上传是一个一个上传，做步骤分解
    //要带上文件数量，为了计算最后生成的sheet数量，还要带上唯一标识，标定当前上传这个流程/用户
    //let exData = {
    //						fileCount: this.filesCounts,
    //						token: this.K_token,
    //					}
//使用File对象可以减少内存消耗，而InputStream需要更多内存，因为它必须缓冲整个文件。

    @PostMapping("/uploadFile")
    public Result uploadFile(MultipartFile file, String exData) {
        if (file == null) {
            return ResultGenerator.genFailResult("NoData");
        }
        JSONObject exOb = JSON.parseObject(exData);
        String tk = exOb.getString("token");
        int fileCount = exOb.getInteger("fileCount");


        File boxFile = null;
        List<File> inputFiles = new ArrayList<>();

        String fileName = file.getOriginalFilename();// 文件原名称
        if (isExcel(Objects.requireNonNull(fileName))) {

            String nameCache = G_inUploadServiceNames.get(tk + fileName);

            Lg.i("uploadFile>>G_inUploadService", "上传计数>:", G_inUploadService.keySet(), ">>>计数", fileCount);
            String tokenFolder = "/" + tk;//当前批，统一文件夹
            if (StringUtils.isNullOrEmpty(nameCache)) {
                G_inUploadServiceNames.put(tk + fileName, fileName);
                try {
                    if (fileName.contains("箱规")) {//箱规进专属
                        String path = furuiStockService.saveToAssets(C_BaseBoxFloder, file.getInputStream(), fileName);
                        if (path == null) {
                            return ResultGenerator.genFailResult(fileName + "箱规文件存储失败");
                        }
                        if (fileCount == 1) {//只传了箱规
                            return ResultGenerator.genSuccessResult("箱规上传成功");
                        }
                    } else {
                        String path = furuiStockService.saveToAssets(tokenFolder, file.getInputStream(), fileName);
                        if (path == null) {
                            return ResultGenerator.genFailResult(fileName + "文件存储失败");
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    return ResultGenerator.genFailResult(fileName + "文件读取失败");
                }

            }
            Set<String> filteredSet = forMultiUserUpload(tk);
            if (filteredSet.size() == fileCount) {

                Lg.i("uploadFile>>G_inUploadServiceNames-文件完整", "上传计数:", G_inUploadServiceNames.keySet(), ">>>计数", fileCount);
                String[] keyArrays = filteredSet.toArray(new String[0]);

//                boolean t = true;
                boolean isHasBoxRule = false;
//                for (int i = 0; i < fileCount; i++) {
//
//                    String fileNameIn = keyArrays[i];
//
//                    if (fileNameIn.contains("箱规")) {//混有箱规>>箱规都进入专属文件夹,这儿不管，箱规都从专属取
////                        isHasBoxRule = true;
////                        inputBox = new ByteArrayInputStream(G_inUploadService.get(fileNameIn));
//                    } else {
//
//
//                    }
//                }

                File[] orders = furuiStockService.getLatestBoxRules(tokenFolder);
                if (orders == null) {
                    return ResultGenerator.genFailResult("货件文件夹读取失败");
                }
                for (int i = 0; i < orders.length; i++) {

                    inputFiles.add(orders[i]);

                }


                if (!isHasBoxRule) { //箱规都从专属取

                    File[] rules = furuiStockService.getLatestBoxRules(null);
                    if (rules == null) {
                        //清下缓存
                        for (String keyOld : keyArrays) {
                            G_inUploadServiceNames.remove(keyOld);
                        }
                        return ResultGenerator.genFailResult("【箱规】文件缺失");
                    } else {
                        boxFile = rules[0];

                    }

                }


                if (boxFile == null) {
                    //清下缓存
                    for (String keyOld : keyArrays) {
                        G_inUploadServiceNames.remove(keyOld);
                    }
                    return ResultGenerator.genFailResult("【箱规】文件缺失2");
                }

                //文件数量不一致>无用判断？？


                String outName = "T" + CommonUtils.getStringMonth() + "-" + "仓库备货表";
                Result result = furuiStockService.excelActionMergeAllFile(outName, boxFile, inputFiles);
                //清下缓存
                for (String keyOld : keyArrays) {
                    G_inUploadServiceNames.remove(keyOld);
                }

                return result;
            }

            Lg.i("uploadFile", "上传额外参数:", JSON.parseObject(exData), boxFile, inputFiles);
            return ResultGenerator.genSuccessResult();
        } else {
            return ResultGenerator.genFailResult("文件格式不正确");
        }

    }


    @PostMapping("/uploadBeihuo")
    public Result uploadBeihuo(MultipartFile file, String exData) {
        if (file == null) {
            return ResultGenerator.genFailResult("NoData");
        }
        JSONObject exOb = JSON.parseObject(exData);
        String tk = exOb.getString("token");
        int fileCount = exOb.getInteger("fileCount");
        boolean isCM = exOb.getBoolean("isCM");


        File boxFile = null;
        List<File> inputFiles = new ArrayList<>();
        Map<String, List<DepotRes>> lastInfosMap = null;
        String fileName = file.getOriginalFilename();// 文件原名称
        if (isExcel(Objects.requireNonNull(fileName))) {

            String nameCache = G_inUploadServiceNames.get(tk + fileName);

            Lg.i("uploadFile>>G_inUploadService", "上传计数>:", G_inUploadService.keySet(), ">>>计数", fileCount);
            String tokenFolder = "/" + tk;//当前批，统一文件夹
            if (StringUtils.isNullOrEmpty(nameCache)) {
                G_inUploadServiceNames.put(tk + fileName, fileName);
                try {
                    if (fileName.contains("箱规")) {//箱规进专属
                        String path = furuiStockService.saveToAssets(C_BaseBoxFloder, file.getInputStream(), fileName);
                        if (path == null) {
                            return ResultGenerator.genFailResult(fileName + "箱规文件存储失败");
                        }
                        if (fileCount == 1) {//只传了箱规
                            return ResultGenerator.genSuccessResult("箱规上传成功");
                        }
                    } else {


                        String path = furuiStockService.saveToAssets(tokenFolder, file.getInputStream(), fileName);
                        if (path == null) {
                            return ResultGenerator.genFailResult(fileName + "文件存储失败");
                        }
                        //获取上一个周期的备货信息
                        lastInfosMap = furuiBeihuoService.getLocalJson();


                        //保存上一个周期的记录，来比较差值
                        furuiBeihuoService.saveLastBeihuo(path);

                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    return ResultGenerator.genFailResult(fileName + "文件读取失败");
                }

            }
            Set<String> filteredSet = forMultiUserUpload(tk);
            if (filteredSet.size() == fileCount) {

                Lg.i("uploadFile>>G_inUploadServiceNames-文件完整", "上传计数:", G_inUploadServiceNames.keySet(), ">>>计数", fileCount);
                String[] keyArrays = filteredSet.toArray(new String[0]);

//                boolean t = true;
                boolean isHasBoxRule = false;
//                for (int i = 0; i < fileCount; i++) {
//
//                    String fileNameIn = keyArrays[i];
//
//                    if (fileNameIn.contains("箱规")) {//混有箱规>>箱规都进入专属文件夹,这儿不管，箱规都从专属取
////                        isHasBoxRule = true;
////                        inputBox = new ByteArrayInputStream(G_inUploadService.get(fileNameIn));
//                    } else {
//
//
//                    }
//                }

                File[] orders = furuiStockService.getLatestBoxRules(tokenFolder);
                if (orders == null) {
                    return ResultGenerator.genFailResult("货件文件夹读取失败");
                }
                for (int i = 0; i < orders.length; i++) {

                    inputFiles.add(orders[i]);

                }


                if (!isHasBoxRule) { //箱规都从专属取

                    File[] rules = furuiStockService.getLatestBoxRules(null);
                    if (rules == null) {
                        //清下缓存
                        for (String keyOld : keyArrays) {
                            G_inUploadServiceNames.remove(keyOld);
                        }
                        return ResultGenerator.genFailResult("【箱规】文件缺失");
                    } else {
                        boxFile = rules[0];

                    }

                }


                if (boxFile == null) {
                    //清下缓存
                    for (String keyOld : keyArrays) {
                        G_inUploadServiceNames.remove(keyOld);
                    }
                    return ResultGenerator.genFailResult("【箱规】文件缺失2");
                }

                //文件数量不一致>无用判断？？


//                String outName = "T" + CommonUtils.getStringMonth() + "-" + "仓库备货表";
//                Result result = furuiStockService.excelActionMergeAllFile(outName, boxFile, inputFiles);

                Result result = furuiBeihuoService.excelActionMergeBehuo(inputFiles.get(0), tk, isCM, lastInfosMap);
                //清下缓存
                for (String keyOld : keyArrays) {
                    G_inUploadServiceNames.remove(keyOld);
                }

                return result;
            }

            Lg.i("uploadFile", "上传额外参数:", JSON.parseObject(exData), boxFile, inputFiles);
            return ResultGenerator.genSuccessResult();
        } else {
            return ResultGenerator.genFailResult("文件格式不正确");
        }

    }


    /*  isFurui 0 furuian 1 anbu 2 cm
     *功能描述
     * @author lch
     * @date 2023/10/31
     * @param  * @param file
     * @param exData
     * @return com.magicair.webpj.core.Result
     */
    @PostMapping("/uploadAMZcsv")
    public Result uploadAMZcsv(MultipartFile file, String exData) {
        if (file == null) {
            return ResultGenerator.genFailResult("NoData");
        }
        JSONObject exOb = JSON.parseObject(exData);
        String tk = exOb.getString("token");
        int fileCount = exOb.getInteger("fileCount");


//        boolean isFurui = exOb.getBoolean("isFurui");
        Integer isFuruiX = exOb.getInteger("isFurui");

        TEMPLATE_company isFurui = TEMPLATE_company.Furui.rawRelate(isFuruiX);

        boolean isUS = exOb.getBoolean("isUS");


        File boxFile = null;
        File shenbaoFile = null;
        List<File> inputFiles = new ArrayList<>();

        String fileName = file.getOriginalFilename();// 文件原名称
        if (isCsv(Objects.requireNonNull(fileName))) {

            String nameCache = G_inUploadServiceNames.get(tk + fileName);

            Lg.i("uploadFile>>G_inUploadService", "上传计数>:", G_inUploadService.keySet(), ">>>计数", fileCount);
            String tokenFolder = "/" + tk;//当前批，统一文件夹
            if (StringUtils.isNullOrEmpty(nameCache)) {
                G_inUploadServiceNames.put(tk + fileName, fileName);
                try {
                    String path = furuiStockService.saveToAssets(tokenFolder, file.getInputStream(), fileName);
                    if (path == null) {
                        return ResultGenerator.genFailResult(fileName + "文件存储失败");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    return ResultGenerator.genFailResult(fileName + "文件读取失败");
                }

            }

            Set<String> filteredSet = forMultiUserUpload(tk);
            if (filteredSet.size() == fileCount) {


                Lg.i("uploadAMZcsv>>G_inUploadServiceNames-文件完整", "上传计数:", G_inUploadServiceNames.keySet(), ">>>计数", fileCount);
                String[] keyArrays = filteredSet.toArray(new String[0]);


                File[] rules = furuiStockService.getLatestBoxRules(null);
                if (rules == null) {
                    //清下缓存
                    for (String keyOld : keyArrays) {
                        G_inUploadServiceNames.remove(keyOld);
                    }
                    return ResultGenerator.genFailResult("【箱规】文件缺失");
                } else {
                    boxFile = rules[0];

                }

                File[] shenBaoFiles = furuiStockService.getLatestBoxRules(C_BaseShenbaoInfoFloder);
                if (shenBaoFiles == null) {
                    //清下缓存
                    for (String keyOld : keyArrays) {
                        G_inUploadServiceNames.remove(keyOld);
                    }
                    return ResultGenerator.genFailResult("【申报要素】文件缺失");
                } else {
                    shenbaoFile = shenBaoFiles[0];

                }


                File[] orders = furuiAMZcsvService.getUploads(tokenFolder);
                if (orders == null) {
                    return ResultGenerator.genFailResult("货件文件夹读取失败");
                }
                inputFiles.addAll(Arrays.asList(orders));

                //文件数量不一致>无用判断？？

//                String outName = "仓库数据";
                String outName = tk;//用于打包多个 仓库数据 + 报关数据
                Result result = furuiAMZcsvService.excelActionMergeAllCSVFile(outName, inputFiles, boxFile, shenbaoFile, isFurui, isUS);
                //清下缓存
                for (String keyOld : keyArrays) {
                    G_inUploadServiceNames.remove(keyOld);
                }

                return result;
            }

            Lg.i("uploadFile", "上传额外参数:", JSON.parseObject(exData), boxFile, inputFiles);
            return ResultGenerator.genSuccessResult();
        } else {
            return ResultGenerator.genFailResult("文件格式不正确");
        }

    }

    @PostMapping("/uploadAMZcsvFee")
    public Result uploadAMZcsvFee(MultipartFile file, String exData) {
        if (file == null) {
            return ResultGenerator.genFailResult("NoData");
        }
        JSONObject exOb = JSON.parseObject(exData);
        String tk = exOb.getString("token");
        int fileCount = exOb.getInteger("fileCount");


        File boxFile = null;
        List<File> inputFiles = new ArrayList<>();

        String fileName = file.getOriginalFilename();// 文件原名称
        if (isCsv(Objects.requireNonNull(fileName))) {

            String nameCache = G_inUploadServiceNames.get(tk + fileName);

            Lg.i("uploadFile>>G_inUploadService", "上传计数>:", G_inUploadService.keySet(), ">>>计数", fileCount);
            String tokenFolder = "/" + tk;//当前批，统一文件夹
            if (StringUtils.isNullOrEmpty(nameCache)) {
                G_inUploadServiceNames.put(tk + fileName, fileName);
                try {
                    String path = furuiStockService.saveToAssets(tokenFolder, file.getInputStream(), fileName);
                    if (path == null) {
                        return ResultGenerator.genFailResult(fileName + "文件存储失败");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    return ResultGenerator.genFailResult(fileName + "文件读取失败");
                }

            }
            Set<String> filteredSet = forMultiUserUpload(tk);
            if (filteredSet.size() == fileCount) {


                Lg.i("uploadAMZcsv>>G_inUploadServiceNames-文件完整", "上传计数:", G_inUploadServiceNames.keySet(), ">>>计数", fileCount);
                String[] keyArrays = filteredSet.toArray(new String[0]);


                File[] rules = furuiStockService.getLatestBoxRules(null);
                if (rules == null) {
                    //清下缓存
                    for (String keyOld : keyArrays) {
                        G_inUploadServiceNames.remove(keyOld);
                    }
                    return ResultGenerator.genFailResult("【箱规】文件缺失");
                } else {
                    boxFile = rules[0];

                }


                File[] orders = furuiAMZcsvService.getUploads(tokenFolder);
                if (orders == null) {
                    return ResultGenerator.genFailResult("货件文件夹读取失败");
                }
                inputFiles.addAll(Arrays.asList(orders));

                //文件数量不一致>无用判断？？

                String outName = "计费重汇总";
                Result result = furuiAMZcsvFeeService.excelActionMergeAllCSVFile(outName, inputFiles, boxFile);
                //清下缓存
                for (String keyOld : keyArrays) {
                    G_inUploadServiceNames.remove(keyOld);
                }

                return result;
            }

            Lg.i("uploadFile", "上传额外参数:", JSON.parseObject(exData), boxFile, inputFiles);
            return ResultGenerator.genSuccessResult();
        } else {
            return ResultGenerator.genFailResult("文件格式不正确");
        }

    }

    @PostMapping("/uploadAMZcsvTrustDeed")
    public Result uploadAMZcsvTrustDeed(MultipartFile file, String exData) {
        if (file == null) {
            return ResultGenerator.genFailResult("NoData");
        }
        JSONObject exOb = JSON.parseObject(exData);
        String tk = exOb.getString("token");
        int fileCount = exOb.getInteger("fileCount");


        File boxFile = null;
        File shenbaoFile = null;
        List<File> inputFiles = new ArrayList<>();

        String fileName = file.getOriginalFilename();// 文件原名称
        if (isCsv(Objects.requireNonNull(fileName)) || isExcel(fileName)) {

            //G_inUploadServiceNames 全局按tk分段保证多线程？？!!!>>>tk前端当前调用会是唯一标识
            String nameCache = G_inUploadServiceNames.get(tk + fileName);

            Lg.i("uploadFile>>G_inUploadService", "上传计数>:", G_inUploadService.keySet(), ">>>计数", fileCount);
            String tokenFolder = "/" + tk;//当前批，统一文件夹
            if (StringUtils.isNullOrEmpty(nameCache)) {
                G_inUploadServiceNames.put(tk + fileName, fileName);
                try {
                    String path = furuiStockService.saveToAssets(tokenFolder, file.getInputStream(), fileName);
                    if (path == null) {
                        return ResultGenerator.genFailResult(fileName + "文件存储失败");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    return ResultGenerator.genFailResult(fileName + "文件读取失败");
                }

            }
            Set<String> filteredSet = forMultiUserUpload(tk);
            if (filteredSet.size() == fileCount) {

                Lg.i("uploadAMZcsv>>G_inUploadServiceNames-文件完整", "上传计数:", G_inUploadServiceNames.keySet(), ">>filter分段" + tk, filteredSet, ">>>计数", fileCount);
                String[] keyArrays = filteredSet.toArray(new String[0]);

                File[] orders = furuiAMZcsvService.getUploads(tokenFolder);
                if (orders == null) {
                    return ResultGenerator.genFailResult("货件文件夹读取失败");
                }
                inputFiles.addAll(Arrays.asList(orders));

                //文件数量不一致>无用判断？？
                String date = CommonUtils.getStringMonthAndDay();
                String outName = date + "-托书";

                Map<String, List<File>> mapFilter = furuiAMZcsvTrustDeedService.filterByForLoop(inputFiles, "货件信息表");

                List<File> cargoInfo = mapFilter.get("info");
                List<File> cargoAllData = mapFilter.get("data");
                if (cargoInfo.size() == 0) {//取出货件信息表
                    return ResultGenerator.genFailResult("缺少货件信息表");
                }
                if (cargoAllData.size() == 0) {//没上传AMZ导出文件
                    return ResultGenerator.genFailResult("缺少亚马逊导出内容");
                }
                if (cargoInfo.size() > 1) {//取出货件信息表
                    return ResultGenerator.genFailResult("暂不支持多个货件信息表");
                }

//                String cargo = "/Users/lch/Documents/Documents/Furui/数据包10.10/货件信息表.xlsx";
//                File catgoFile = new File(cargo);
                File catgoFile = cargoInfo.get(0);
                WrapListWithMsg<CargoInfo> cargoInfoListFast = null;
                try {

                    cargoInfoListFast = PoiUtiles.getCargoInfoListFast(catgoFile);
                } catch (IOException e) {
//                    e.printStackTrace();
                    return ResultGenerator.genFailResult(catgoFile.getName() + "读取失败");
                }

                File[] shenBaoFiles = furuiStockService.getLatestBoxRules(C_BaseShenbaoInfoFloder);
                if (shenBaoFiles == null) {
                    //清下缓存
                    for (String keyOld : keyArrays) {
                        G_inUploadServiceNames.remove(keyOld);
                    }
                    return ResultGenerator.genFailResult("【申报要素】文件缺失");
                } else {
                    shenbaoFile = shenBaoFiles[0];

                }

                //申报要素
                WrapListWithMsg<ShenBaoInfo> shenBaoWrap = null;
                try {
                    shenBaoWrap = PoiUtiles.getShenBaoInfoList(shenbaoFile);
                    if (shenBaoWrap != null && shenBaoWrap.getErrMsg() != null && shenBaoWrap.getErrMsg().length() > 2) {//格式校验有误
                        return ResultGenerator.genFailResult("解析申报要素文件失败", shenBaoWrap.getErrMsg());
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    return ResultGenerator.genFailResult("解析申报要素文件失败", e);
                }
                //申报要素按照款号分组！
                Map<String, List<ShenBaoInfo>> shenBaoGroup = shenBaoWrap.getListData().stream().collect(Collectors.groupingBy(ShenBaoInfo::getStyle));


                File[] rules = furuiStockService.getLatestBoxRules(null);
                if (rules == null) {
                    //清下缓存
                    for (String keyOld : keyArrays) {
                        G_inUploadServiceNames.remove(keyOld);
                    }
                    return ResultGenerator.genFailResult("【箱规】文件缺失");
                } else {
                    boxFile = rules[0];

                }

                //箱规，暂时主要获取erp 款号
                WrapListWithMsg<BoxRules> boxRulesWrap = null;
                try {
                    boxRulesWrap = PoiUtiles.getBoxRulesList(null, null, boxFile);
                    if (boxRulesWrap != null && boxRulesWrap.getErrMsg() != null && boxRulesWrap.getErrMsg().length() > 2) {//格式校验有误
                        return ResultGenerator.genFailResult("解析箱规文件失败", boxRulesWrap.getErrMsg());
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    return ResultGenerator.genFailResult("解析箱规文件失败", e);
                }

                //箱规分组！
                Map<String, List<BoxRules>> boxRulesGroup = boxRulesWrap.getListData().stream().collect(Collectors.groupingBy(BoxRules::getSKU));


                Result result = furuiAMZcsvTrustDeedService.excelActionMergeAllCSVFile(tk, cargoAllData, cargoInfoListFast, shenBaoGroup, boxRulesGroup, outName);


                //清下缓存
                for (String keyOld : keyArrays) {
                    G_inUploadServiceNames.remove(keyOld);
                }

                return result;
            }

            Lg.i("uploadFile", "上传额外参数:", JSON.parseObject(exData), boxFile, inputFiles);
            return ResultGenerator.genSuccessResult();
        } else {
            return ResultGenerator.genFailResult("文件格式不正确");
        }

    }


    //建货件,根据SKU 入箱
    @PostMapping("/uploadHJ")
    public Result uploadHJ(MultipartFile file, String exData) {
        if (file == null) {
            return ResultGenerator.genFailResult("NoData");
        }
        JSONObject exOb = JSON.parseObject(exData);
        String tk = exOb.getString("token");
        int fileCount = exOb.getInteger("fileCount");


        File boxFile = null;
        List<File> inputFiles = new ArrayList<>();

        String fileName = file.getOriginalFilename();// 文件原名称
        if (isExcel(Objects.requireNonNull(fileName))) {

            String nameCache = G_inUploadServiceNames.get(tk + fileName);

            Lg.i("uploadFile>>G_inUploadService", "上传计数>:", G_inUploadService.keySet(), ">>>计数", fileCount);
            String tokenFolder = "/" + tk;//当前批，统一文件夹
            if (StringUtils.isNullOrEmpty(nameCache)) {
                G_inUploadServiceNames.put(tk + fileName, fileName);
                try {
                    String path = furuiStockService.saveToAssets(tokenFolder, file.getInputStream(), fileName);
                    if (path == null) {
                        return ResultGenerator.genFailResult(fileName + "文件存储失败");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    return ResultGenerator.genFailResult(fileName + "文件读取失败");
                }

            }
            Set<String> filteredSet = forMultiUserUpload(tk);
            if (filteredSet.size() == fileCount) {


                Lg.i("uploadAMZcsv>>G_inUploadServiceNames-文件完整", "上传计数:", G_inUploadServiceNames.keySet(), ">>>计数", fileCount);
                String[] keyArrays = filteredSet.toArray(new String[0]);


                File[] orders = furuiAMZcsvService.getUploads(tokenFolder);
                if (orders == null) {
                    return ResultGenerator.genFailResult("暂时只支持xlsx格式");
                }
                inputFiles.addAll(Arrays.asList(orders));

                //文件数量不一致>无用判断？？

                String outName = "建货件";
                Result result = furuiSKUPutInBoxes.excelAction(outName + tk, inputFiles);
                //清下缓存
                for (String keyOld : keyArrays) {
                    G_inUploadServiceNames.remove(keyOld);
                }

                return result;
            }

            Lg.i("uploadFile", "上传额外参数:", JSON.parseObject(exData), boxFile, inputFiles);
            return ResultGenerator.genSuccessResult();
        } else {
            return ResultGenerator.genFailResult("文件格式不正确");
        }

    }


    private Set<String> forMultiUserUpload(String currtenTk) {//上传当时时间戳作为当前流程id
        Set<String> allFilesSet = G_inUploadServiceNames.keySet();//G_inUploadServiceNames的容量限制？？
        // tk当前分段，应对并发!!
        Lg.i("forMultiUserUpload>>分批>>>>", currtenTk);
        Set<String> filteredSet = allFilesSet.stream()
                .filter(s -> s.startsWith(currtenTk)) // 过滤条件
//                .filter(s -> s.equals(currtenTk)) // 过滤条件，是拼接不能用相等！！
                .collect(Collectors.toSet()); // 收集结果

        return filteredSet;
    }

}
