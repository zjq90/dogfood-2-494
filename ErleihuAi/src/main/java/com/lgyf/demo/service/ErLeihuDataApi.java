package com.lgyf.demo.service;

import com.alibaba.fastjson.JSON;
import com.lgyf.demo.bean.*;
import com.lgyf.demo.config.TradeDoArgs;
import com.lgyf.demo.dao.ClientDao;
import com.lgyf.demo.pojo.ErleihuApi;
import com.lgyf.demo.util.*;
import com.pab.is.obp.easysdk.client.model.*;
import com.pingan.api.util.FileUploadResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

import javax.annotation.Resource;
import java.io.File;
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
     * 身份证上传接口
     * @param args
     * @return
     */
    public Map<String,String> uploadIdCard(@RequestBody Map<String, String> args) {
        StringBuffer log = new StringBuffer();
        log.append("身份证上传参数:" + JSON.toJSONString(args) + "\n");
        Map<String, String> mapMeta = new HashMap<String, String>();
        mapMeta.put("uuid", args.get("uuid"));
        mapMeta.put("suuid", suuid);
        String client_no = args.get("client_no");
        String client_public_key = client.getClient_public_key();
        String cert_no = args.get("cert_no");
        String name = args.get("name");
        String cert_pic_front = args.get("cert_pic_front");
        String cert_pic_reverse = args.get("cert_pic_reverse");
        String msg = "成功";

        try {
            // 调用图片服务器上传方法
            Map<String, String> uploadResult = uploadImageToServer(cert_pic_front, cert_pic_reverse, cert_no);
            String uploadCode = uploadResult.get("code");
            String uploadMsg = uploadResult.get("msg");
            log.append("图片服务器上传结果:code=" + uploadCode + ",msg=" + uploadMsg + "\n");

            if ("err".equals(uploadCode)) {
                log.append("图片上传失败:" + uploadMsg + "\n");
                return TradeDoArgs.return_error(args, "010", uploadMsg);
            }

            // 获取图片保存路径
            String cert_pic_front_path = uploadResult.get("cert_pic_front_path");
            String cert_pic_reverse_path = uploadResult.get("cert_pic_reverse_path");

            // 封装二类户专用通道方法参数
            Map<String, String> obpArgs = new HashMap<>();
            obpArgs.put("cert_pic_front_path", cert_pic_front_path);
            obpArgs.put("cert_pic_reverse_path", cert_pic_reverse_path);
            obpArgs.put("cert_no", cert_no);
            obpArgs.put("name", name);

            // 设置suuid到erleihuApi
            erleihuApi.setSuuid(suuid);

            // 调用二类户专用通道方法
            Map<String, String> obpResult = ErleihuApi.obpApiIbankAcctWefileId(obpArgs);
            String obpCode = obpResult.get("code");
            String obpMsg = obpResult.get("msg");
            log.append("二类户专用通道调用结果:code=" + obpCode + ",msg=" + obpMsg + "\n");

            if ("fail".equals(obpCode)) {
                log.append("二类户专用通道调用失败:" + obpMsg + "\n");
                return TradeDoArgs.return_error(args, "011", obpMsg);
            }

            // 获取身份证申请号
            String cert_order_no = obpResult.get("cert_order_no");

            // 封装返回参数
            mapMeta.put("name", name);
            mapMeta.put("cert_no", cert_no);
            mapMeta.put("cert_order_no", cert_order_no);

            // 保存上传记录到数据库
            UploadCert uploadCert = new UploadCert();
            uploadCert.setClient_no(client_no);
            uploadCert.setCert_no(cert_no);
            uploadCert.setName(name);
            uploadCert.setUpload_status("1"); // 1为成功
            uploadCert.setCert_order_no(cert_order_no);
            uploadCert.setMsg(msg);

            try {
                int insertResult = clientDao.insert("upload_cert", uploadCert);
                if (insertResult > 0) {
                    log.append("上传记录保存成功\n");
                } else {
                    log.append("上传记录保存失败\n");
                }
            } catch (Exception e) {
                log.append("保存上传记录异常:" + e.getMessage() + "\n");
                error.error(suuid + "保存上传记录异常", e);
            }

            log.append("身份证上传成功,cert_order_no=" + cert_order_no + "\n");
            return TradeDoArgs.return_success(mapMeta, client_no, client_public_key, api_private_key, msg);

        } catch (Exception e) {
            log.append(suuid + "身份证上传异常\n");
            error.error(suuid + "身份证上传uploadIdCard", e);
            return TradeDoArgs.return_error(args, "002", "身份证上传异常");
        } finally {
            logger.info(log.toString());
        }
    }

    /**
     * 上传图片到服务器
     * @param cert_pic_front 身份证正面图片hex字符串
     * @param cert_pic_reverse 身份证反面图片hex字符串
     * @param cert_no 身份证号
     * @return 上传结果，包含code、msg、图片路径
     */
    public Map<String, String> uploadImageToServer(String cert_pic_front, String cert_pic_reverse, String cert_no) {
        Map<String, String> result = new HashMap<>();
        StringBuffer log = new StringBuffer();
        log.append("上传图片到服务器开始,cert_no=" + cert_no + "\n");

        try {
            // 参数非空验证
            if (cert_pic_front == null || cert_pic_front.trim().isEmpty()) {
                result.put("code", "err");
                result.put("msg", "身份证正面图片不能为空");
                log.append("身份证正面图片为空\n");
                return result;
            }
            if (cert_pic_reverse == null || cert_pic_reverse.trim().isEmpty()) {
                result.put("code", "err");
                result.put("msg", "身份证反面图片不能为空");
                log.append("身份证反面图片为空\n");
                return result;
            }
            if (cert_no == null || cert_no.trim().isEmpty()) {
                result.put("code", "err");
                result.put("msg", "身份证号不能为空");
                log.append("身份证号为空\n");
                return result;
            }

            // 获取图片保存路径
            String uploadPath = idcardUrl;
            if (!uploadPath.endsWith("/")) {
                uploadPath += "/";
            }

            // 创建目录
            File uploadDir = new File(uploadPath);
            if (!uploadDir.exists()) {
                uploadDir.mkdirs();
            }

            // 以cert_no命名图片
            String frontFileName = cert_no + "_front.jpg";
            String reverseFileName = cert_no + "_reverse.jpg";
            String frontFilePath = uploadPath + frontFileName;
            String reverseFilePath = uploadPath + reverseFileName;

            // 将hex字符串转换为byte数组并保存为图片
            byte[] frontBytes = PicUtils.hex2byte(cert_pic_front);
            byte[] reverseBytes = PicUtils.hex2byte(cert_pic_reverse);

            boolean frontSaved = PicUtils.byte2image(frontBytes, frontFilePath);
            boolean reverseSaved = PicUtils.byte2image(reverseBytes, reverseFilePath);

            if (!frontSaved || !reverseSaved) {
                result.put("code", "err");
                result.put("msg", "图片保存失败");
                log.append("图片保存失败,frontSaved=" + frontSaved + ",reverseSaved=" + reverseSaved + "\n");
                return result;
            }

            log.append("图片保存成功,frontPath=" + frontFilePath + ",reversePath=" + reverseFilePath + "\n");
            result.put("code", "success");
            result.put("msg", "图片上传成功");
            result.put("cert_pic_front_path", frontFilePath);
            result.put("cert_pic_reverse_path", reverseFilePath);
            return result;

        } catch (Exception e) {
            log.append("上传图片异常:" + e.getMessage() + "\n");
            error.error(suuid + "上传图片到服务器异常", e);
            result.put("code", "err");
            result.put("msg", "图片上传异常:" + e.getMessage());
            return result;
        } finally {
            logger.info(log.toString());
        }
    }

}
