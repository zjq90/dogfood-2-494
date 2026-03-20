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
                  insert=2;
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

    public Map<String,String> upload_cert(@RequestBody Map<String, String> args) {
        StringBuffer log = new StringBuffer();
        log.append("身份证上传参数:" + JSON.toJSONString(args) + "\n");
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
            Map<String, String> uploadResult = uploadCertPic(cert_pic_front, cert_pic_reverse, cert_no);
            String uploadCode = uploadResult.get("code");
            String uploadMsg = uploadResult.get("msg");
            
            if ("err".equals(uploadCode)) {
                log.append("图片上传失败:" + uploadMsg + "\n");
                return TradeDoArgs.return_error(args, "009", uploadMsg);
            }
            
            String cert_pic_front_path = uploadResult.get("cert_pic_front_path");
            String cert_pic_reverse_path = uploadResult.get("cert_pic_reverse_path");
            
            Map<String, String> apiArgs = new HashMap<String, String>();
            apiArgs.put("cert_pic_front_path", cert_pic_front_path);
            apiArgs.put("cert_pic_reverse_path", cert_pic_reverse_path);
            apiArgs.put("cert_no", cert_no);
            apiArgs.put("name", name);
            
            ErleihuApi.setSuuid(suuid);
            Map<String, String> apiResult = ErleihuApi.obpApiIbankAcctWefileId(apiArgs);
            String apiCode = apiResult.get("code");
            String apiMsg = apiResult.get("msg");
            
            if ("fail".equals(apiCode)) {
                log.append("二类户专用通道调用失败:" + apiMsg + "\n");
                return TradeDoArgs.return_error(args, "010", apiMsg);
            }
            
            String cert_order_no = apiResult.get("cert_order_no");
            mapMeta.put("name", name);
            mapMeta.put("cert_no", cert_no);
            mapMeta.put("cert_order_no", cert_order_no);
            
            log.append("身份证上传成功, cert_order_no:" + cert_order_no + "\n");
            
            saveUploadCertLog(client_no, cert_no, name, cert_order_no, "1", "上传成功", log);
            
            return TradeDoArgs.return_success(mapMeta, client_no, client_public_key, api_private_key, "成功", "01");
        } catch (Exception e) {
            log.append(suuid + "身份证上传异常\n");
            error.error(suuid + "身份证上传upload_cert", e);
            saveUploadCertLog(client_no, cert_no, name, "", "0", "上传异常:" + e.getMessage(), log);
            return TradeDoArgs.return_error(args, "002", "身份证上传异常");
        } finally {
            logger.info(log.toString());
        }
    }

    public Map<String, String> uploadCertPic(String cert_pic_front, String cert_pic_reverse, String cert_no) {
        Map<String, String> result = new HashMap<String, String>();
        
        if (cert_pic_front == null || cert_pic_front.trim().equals("")) {
            result.put("code", "err");
            result.put("msg", "身份证正面图片不能为空");
            return result;
        }
        if (cert_pic_reverse == null || cert_pic_reverse.trim().equals("")) {
            result.put("code", "err");
            result.put("msg", "身份证反面图片不能为空");
            return result;
        }
        if (cert_no == null || cert_no.trim().equals("")) {
            result.put("code", "err");
            result.put("msg", "身份证号不能为空");
            return result;
        }
        
        try {
            String uploadPath = idcardUrl;
            if (!uploadPath.endsWith(File.separator)) {
                uploadPath = uploadPath + File.separator;
            }
            
            File dir = new File(uploadPath);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            
            byte[] frontBytes = PicUtils.hex2byte(cert_pic_front);
            byte[] reverseBytes = PicUtils.hex2byte(cert_pic_reverse);
            
            String frontPath = uploadPath + cert_no + "_front.jpg";
            String reversePath = uploadPath + cert_no + "_reverse.jpg";
            
            boolean frontResult = PicUtils.byte2image(frontBytes, frontPath);
            boolean reverseResult = PicUtils.byte2image(reverseBytes, reversePath);
            
            if (!frontResult) {
                result.put("code", "err");
                result.put("msg", "身份证正面图片保存失败");
                return result;
            }
            if (!reverseResult) {
                result.put("code", "err");
                result.put("msg", "身份证反面图片保存失败");
                return result;
            }
            
            result.put("code", "success");
            result.put("msg", "图片上传成功");
            result.put("cert_pic_front_path", frontPath);
            result.put("cert_pic_reverse_path", reversePath);
            return result;
        } catch (Exception e) {
            result.put("code", "err");
            result.put("msg", "图片上传异常:" + e.getMessage());
            return result;
        }
    }

    public void saveUploadCertLog(String client_no, String cert_no, String name, 
                                   String cert_order_no, String upload_status, String msg, StringBuffer log) {
        try {
            UploadCert uploadCert = new UploadCert();
            uploadCert.setClient_no(client_no);
            uploadCert.setCert_no(cert_no);
            uploadCert.setName(name);
            uploadCert.setCert_order_no(cert_order_no);
            uploadCert.setUpload_status(upload_status);
            uploadCert.setMsg(msg);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            uploadCert.setAdd_time(sdf.format(new Date()));
            
            int insertResult = clientDao.insert("ist_upload_cert", uploadCert);
            if (insertResult <= 0) {
                log.append("数据库保存upload_cert记录失败\n");
            }
        } catch (Exception e) {
            log.append("数据库保存upload_cert记录异常:" + e.getMessage() + "\n");
            error.error(suuid + "保存upload_cert记录异常", e);
        }
    }

}
