package com.lgyf.demo.service;

import com.alibaba.fastjson.JSON;
import com.lgyf.demo.bean.*;
import com.lgyf.demo.config.TradeDoArgs;
import com.lgyf.demo.dao.ClientDao;
import com.lgyf.demo.pojo.ErleihuApi;
import com.lgyf.demo.util.*;
import com.pab.is.obp.easysdk.client.model.*;
import com.pingan.api.util.FileUploadResponse;
import lombok.val;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;


import javax.annotation.Resource;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

@SuppressWarnings("all")
@Component
public class ErLeihuDataApi {
    @Autowired
    private RedisCacheService redisCacheService;
    private static String api_private_key=Return.getApi_private_key();
    private static String api_public_key=Return.getApi_public_key();
    private String suuid="";
    private Client client;
    private AgentArgs agentArgs;
    private ErleihuApi erleihuApi = new ErleihuApi();
    private PicUtils picUtils = new PicUtils();
    private static String idcardUrl= ResourceBundle.getBundle("commondata").getString("idcardUpload");
    @Resource
    private ClientDao clientDao;

    private static Logger logger = LoggerFactory.getLogger("info");
    private static Logger error = LoggerFactory.getLogger("error");

    public AgentArgs getAgentArgs() {
        return agentArgs;
    }

    public void setAgentArgs(AgentArgs agentArgs) {
        this.agentArgs = agentArgs;
    }

    public Client getClient() {
        return client;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    public String getSuuid() {
        return suuid;
    }
    public void setSuuid(String suuid) {
        this.suuid = suuid;
    }

    public Map<String,String> get_client_reg(@RequestBody Map<String, String> args) {
        StringBuffer log=new StringBuffer();
        log.append("商户开通二类户参数:"+JSON.toJSONString(args)+"\n");
        Map<String, String> mapMeta = new HashMap<String, String>();
        mapMeta.put("uuid",args.get("uuid"));
        mapMeta.put("suuid",suuid);
        String client_no  = args.get("client_no");
        String client_public_key=agentArgs.getPublic_key();
        String reg_client_no=args.get("reg_client_no");
        String msg="成功";
        Integer insert=0;
        Client reg_client=redisCacheService.getClient(reg_client_no);
        try{
            if(reg_client!=null){
                 msg="企业已开通二类户,不能重复开通";
                 log.append(msg+"\n");
                  insert=2;//无需插入
                 return TradeDoArgs.return_error(args,"014",msg);
            }
            reg_client=new Client();
            String client_name=args.get("client_name");
            reg_client.setClient_no(reg_client_no);
            reg_client.setClient_name(args.get("short_name"));
            reg_client.setIs_agent(0);
            reg_client.setName(client_name);
            reg_client.setPhone(args.get("mobile"));
            reg_client.setSocket_ip(args.get("socket_ip"));
            reg_client.setStatus(1);
            String public_key=args.get("public_key").replace("+","-").replace("/","_").trim();
            reg_client.setClient_public_key(public_key);
            insert=clientDao.insert("ist_client",reg_client);
            mapMeta.put("reg_client_no",reg_client_no);
            mapMeta.put("client_name",client_name);
            return TradeDoArgs.return_success(mapMeta,client_no,client_public_key,api_private_key);
        } catch (Exception e) {
            log.append(suuid+"企业二类户开通异常\n");
            error.error(suuid+"企业二类户开通get_client_reg",e);
            return TradeDoArgs.return_error(args,"002","企业二类户开通异常");
        }finally {
            if(insert==0){
                log.append("插入数据库失败:"+JSON.toJSONString(reg_client)+"\n");
            }
            logger.info(log.toString());
        }
    }

    /**
     * 上传图片到图片服务器
     * @param cert_pic_front 身份证正面图片（hex字符串）
     * @param cert_pic_reverse 身份证反面图片（hex字符串）
     * @param cert_no 身份证号（用于命名图片）
     * @return Map包含code和msg
     */
    public Map<String, String> uploadIdCardImages(String cert_pic_front, String cert_pic_reverse, String cert_no) {
        Map<String, String> result = new HashMap<>();
        StringBuffer log = new StringBuffer();
        log.append("上传身份证图片到服务器[" + suuid + "]\n");

        try {
            // 参数非空验证
            if (cert_pic_front == null || cert_pic_front.trim().equals("")) {
                result.put("code", "err");
                result.put("msg", "身份证正面图片不能为空");
                log.append("身份证正面图片为空\n");
                logger.info(log.toString());
                return result;
            }
            if (cert_pic_reverse == null || cert_pic_reverse.trim().equals("")) {
                result.put("code", "err");
                result.put("msg", "身份证反面图片不能为空");
                log.append("身份证反面图片为空\n");
                logger.info(log.toString());
                return result;
            }
            if (cert_no == null || cert_no.trim().equals("")) {
                result.put("code", "err");
                result.put("msg", "身份证号不能为空");
                log.append("身份证号为空\n");
                logger.info(log.toString());
                return result;
            }

            // 从配置文件获取图片保存路径
            String basePath = idcardUrl;
            if (!basePath.endsWith("/") && !basePath.endsWith("\\")) {
                basePath += "/";
            }

            // 创建日期目录
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
            String dateDir = sdf.format(new Date());
            String savePath = basePath + dateDir + "/";
            File dir = new File(savePath);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            // 使用身份证号命名图片
            String frontFileName = cert_no + "_front.jpg";
            String reverseFileName = cert_no + "_reverse.jpg";
            String frontPath = savePath + frontFileName;
            String reversePath = savePath + reverseFileName;

            // 将hex字符串转换为byte数组并保存图片
            byte[] frontBytes = PicUtils.hex2byte(cert_pic_front);
            byte[] reverseBytes = PicUtils.hex2byte(cert_pic_reverse);

            if (frontBytes == null || frontBytes.length == 0) {
                result.put("code", "err");
                result.put("msg", "身份证正面图片数据无效");
                log.append("身份证正面图片数据转换失败\n");
                logger.info(log.toString());
                return result;
            }
            if (reverseBytes == null || reverseBytes.length == 0) {
                result.put("code", "err");
                result.put("msg", "身份证反面图片数据无效");
                log.append("身份证反面图片数据转换失败\n");
                logger.info(log.toString());
                return result;
            }

            // 保存图片
            boolean frontSaved = PicUtils.byte2image(frontBytes, frontPath);
            boolean reverseSaved = PicUtils.byte2image(reverseBytes, reversePath);

            if (!frontSaved) {
                result.put("code", "err");
                result.put("msg", "身份证正面图片保存失败");
                log.append("身份证正面图片保存失败，路径：" + frontPath + "\n");
                logger.info(log.toString());
                return result;
            }
            if (!reverseSaved) {
                result.put("code", "err");
                result.put("msg", "身份证反面图片保存失败");
                log.append("身份证反面图片保存失败，路径：" + reversePath + "\n");
                logger.info(log.toString());
                return result;
            }

            result.put("code", "success");
            result.put("msg", "图片上传成功");
            result.put("cert_pic_front_path", frontPath);
            result.put("cert_pic_reverse_path", reversePath);
            log.append("图片上传成功，正面：" + frontPath + "，反面：" + reversePath + "\n");
            logger.info(log.toString());
            return result;

        } catch (Exception e) {
            result.put("code", "err");
            result.put("msg", "图片上传异常：" + e.getMessage());
            log.append("图片上传异常：" + e.getMessage() + "\n");
            error.error(suuid + "上传身份证图片异常", e);
            logger.info(log.toString());
            return result;
        }
    }

    /**
     * 身份证上传接口处理（action_code=01）
     * @param args 请求参数
     * @return 响应结果
     */
    public Map<String, String> uploadCert(Map<String, String> args) {
        StringBuffer log = new StringBuffer();
        log.append("身份证上传接口[" + suuid + "]参数:" + JSON.toJSONString(args) + "\n");

        Map<String, String> mapMeta = new HashMap<String, String>();
        mapMeta.put("uuid", args.get("uuid"));
        mapMeta.put("suuid", suuid);

        String client_no = args.get("client_no");
        String client_public_key = client.getClient_public_key();

        String cert_pic_front = args.get("cert_pic_front");
        String cert_pic_reverse = args.get("cert_pic_reverse");
        String cert_no = args.get("cert_no");
        String name = args.get("name");

        try {
            // 1. 上传图片到图片服务器
            Map<String, String> uploadResult = uploadIdCardImages(cert_pic_front, cert_pic_reverse, cert_no);

            // 判断图片上传状态
            if (uploadResult.get("code").equals("err")) {
                // 图片上传失败，封装错误返回
                String errMsg = uploadResult.get("msg");
                log.append("图片上传失败：" + errMsg + "\n");
                logger.info(log.toString());
                return TradeDoArgs.return_error(args, "015", errMsg);
            }

            // 2. 调用二类户专用通道方法
            String cert_pic_front_path = uploadResult.get("cert_pic_front_path");
            String cert_pic_reverse_path = uploadResult.get("cert_pic_reverse_path");

            Map<String, String> erleihuParams = new HashMap<>();
            erleihuParams.put("cert_pic_front_path", cert_pic_front_path);
            erleihuParams.put("cert_pic_reverse_path", cert_pic_reverse_path);
            erleihuParams.put("cert_no", cert_no);
            erleihuParams.put("name", name);

            erleihuApi.setSuuid(suuid);
            Map<String, String> erleihuResult = erleihuApi.obpApiIbankAcctWefileId(erleihuParams);

            // 判断二类户通道调用状态
            if (erleihuResult.get("code").equals("fail")) {
                // 二类户通道调用失败，封装错误返回
                String errMsg = erleihuResult.get("msg");
                log.append("二类户通道调用失败：" + errMsg + "\n");
                logger.info(log.toString());
                return TradeDoArgs.return_error(args, "016", errMsg);
            }

            // 3. 封装成功返回信息
            String cert_order_no = erleihuResult.get("cert_order_no");
            mapMeta.put("name", name);
            mapMeta.put("cert_no", cert_no);
            mapMeta.put("cert_order_no", cert_order_no);

            // 4. 保存到数据库
            UploadCert uploadCert = new UploadCert();
            uploadCert.setClient_no(client_no);
            uploadCert.setCert_no(cert_no);
            uploadCert.setName(name);
            uploadCert.setUpload_status("1"); // 1为成功
            uploadCert.setCert_order_no(cert_order_no);
            uploadCert.setAdd_time(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            uploadCert.setMsg("身份证上传成功");

            try {
                int insertResult = clientDao.insert("ist_upload_cert", uploadCert);
                if (insertResult > 0) {
                    log.append("数据库保存成功\n");
                } else {
                    log.append("数据库保存失败，只记录日志\n");
                }
            } catch (Exception e) {
                log.append("数据库保存异常：" + e.getMessage() + "，只记录日志\n");
                error.error(suuid + "保存身份证上传记录到数据库异常", e);
            }

            log.append("身份证上传成功，cert_order_no：" + cert_order_no + "\n");
            logger.info(log.toString());

            return TradeDoArgs.return_success(mapMeta, client_no, client_public_key, api_private_key, "身份证上传成功");

        } catch (Exception e) {
            log.append(suuid + "身份证上传接口异常\n");
            error.error(suuid + "身份证上传接口uploadCert", e);
            return TradeDoArgs.return_error(args, "002", "身份证上传接口异常");
        } finally {
            logger.info(log.toString());
        }
    }

}
