package com.agriculture.modules.workorder.controller;

import com.agriculture.modules.workorder.dto.CallbackDTO;
import com.agriculture.modules.workorder.dto.SeverityUpdateDTO;
import com.agriculture.modules.workorder.dto.StatusUpdateDTO;
import com.agriculture.modules.workorder.dto.WorkOrderCreateDTO;
import com.agriculture.modules.workorder.dto.WorkOrderManualCreateDTO;
import com.agriculture.modules.workorder.service.WorkOrderService;
import com.agriculture.modules.workorder.vo.CallbackResponseVO;
import com.agriculture.common.vo.Result;
import com.agriculture.common.service.EmailService;
import com.agriculture.modules.workorder.vo.WorkOrderDetailVO;
import com.agriculture.modules.workorder.vo.WorkOrderVO;
import com.agriculture.modules.user.entity.SysUser;
import com.agriculture.modules.user.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/workorder")
public class WorkOrderController {

    @Resource
    private WorkOrderService workOrderService;

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private EmailService emailService;

    @GetMapping("/list")
    public Result<IPage<WorkOrderVO>> listWorkOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        String companyId = resolveCompanyId(userId);
        return Result.success(workOrderService.listWorkOrders(status, severity, startDate, endDate, page, size, companyId));
    }

    @GetMapping("/{id}")
    public Result<WorkOrderDetailVO> getWorkOrderDetail(@PathVariable Long id) {
        return Result.success(workOrderService.getWorkOrderDetail(id));
    }

    @PostMapping("/create")
    public Result<Long> createWorkOrder(@Valid @RequestBody WorkOrderCreateDTO dto,
                                          HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        SysUser currentUser = sysUserMapper.selectById(userId);
        String operatorId = userId;
        String operatorName = currentUser != null ? currentUser.getName() : "系统";
        String companyId = currentUser != null ? currentUser.getCompanyId() : null;
        Long id = workOrderService.createWorkOrder(dto, operatorId, operatorName, companyId);
        return Result.success("工单创建成功", id);
    }

    @PostMapping("/create-manual")
    public Result<Long> createManualWorkOrder(@Valid @RequestBody WorkOrderManualCreateDTO dto,
                                                HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        SysUser currentUser = sysUserMapper.selectById(userId);
        String operatorId = userId;
        String operatorName = currentUser != null ? currentUser.getName() : "系统";
        String companyId = currentUser != null ? currentUser.getCompanyId() : null;
        Long id = workOrderService.createManualWorkOrder(dto, operatorId, operatorName, companyId);
        return Result.success("工单创建成功", id);
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id,
                                     @Valid @RequestBody StatusUpdateDTO dto,
                                     HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        SysUser currentUser = sysUserMapper.selectById(userId);
        String operatorId = userId;
        String operatorName = currentUser != null ? currentUser.getName() : "系统";
        workOrderService.updateStatus(id, dto.getStatus(), dto.getComment(), operatorId, operatorName);
        return Result.success("状态更新成功", null);
    }

    @PutMapping("/{id}/severity")
    public Result<Void> updateSeverity(@PathVariable Long id,
                                       @Valid @RequestBody SeverityUpdateDTO dto) {
        workOrderService.updateSeverity(id, dto.getSeverity());
        return Result.success("严重程度更新成功", null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteWorkOrder(@PathVariable Long id) {
        workOrderService.deleteWorkOrder(id);
        return Result.success("工单已删除", null);
    }

    /**
     * 发送工单邮件通知给指定专家
     */
    @PostMapping("/{id}/send-email")
    public Result<Void> sendWorkOrderEmail(
            @PathVariable Long id,
            @RequestBody SendEmailDTO dto,
            HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        SysUser currentUser = sysUserMapper.selectById(userId);
        if (currentUser == null) {
            return Result.error(401, "未登录");
        }

        // 获取工单详情用于邮件内容
        WorkOrderDetailVO detail = workOrderService.getWorkOrderDetail(id);
        if (detail == null) {
            return Result.error(404, "工单不存在");
        }

        // 获取收件人信息
        SysUser expert = sysUserMapper.selectById(dto.getToUserId());
        if (expert == null || expert.getEmail() == null || expert.getEmail().isEmpty()) {
            return Result.error(400, "收件人邮箱不存在");
        }

        // 构建邮件内容
        String subject = "【农作物疾病检测系统】工单通知 - " + detail.getTitle();
        String text = "尊敬的 " + expert.getName() + "：\n\n"
                + "您有一条新的工单通知，请及时处理。\n\n"
                + "━━━━━━━━━━━━━━━━━━━━\n"
                + "工单标题：" + detail.getTitle() + "\n"
                + "严重程度：" + detail.getSeverity() + "\n"
                + "工单状态：" + detail.getStatus() + "\n"
                + "网格区域：" + detail.getGridLabel() + "\n"
                + "病虫害：" + (detail.getPestName() != null ? detail.getPestName() : "无") + "\n"
                + "置信度：" + (detail.getConfidence() != null ? detail.getConfidence() + "%" : "无") + "\n"
                + "创建时间：" + detail.getCreatedAt() + "\n";

        if (dto.getContent() != null && !dto.getContent().isEmpty()) {
            text += "\n━━━━━━━━━━━━━━━━━━━━\n"
                    + "专家分析：\n" + dto.getContent() + "\n";
        }

        text += "\n━━━━━━━━━━━━━━━━━━━━\n"
                + "请登录系统查看详情并处理。\n\n"
                + "—— 农作物疾病检测系统\n";

        try {
            emailService.sendEmail(expert.getEmail(), subject, text);
            return Result.success("邮件发送成功", null);
        } catch (Exception e) {
            return Result.error(500, "邮件发送失败: " + e.getMessage());
        }
    }

    /** 发送邮件请求DTO */
    public static class SendEmailDTO {
        private String toUserId;
        private String content; // Agent 编写的分析内容

        public String getToUserId() { return toUserId; }
        public void setToUserId(String toUserId) { this.toUserId = toUserId; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }

    /**
     * 根据用户ID解析企业ID，若无则返回空字符串（不过滤）
     */
    private String resolveCompanyId(String userId) {
        if (userId == null) return "";
        SysUser user = sysUserMapper.selectById(userId);
        if (user != null && user.getCompanyId() != null && !user.getCompanyId().isEmpty()) {
            return user.getCompanyId();
        }
        return "";
    }

    @PostMapping("/callback")
    public Result<CallbackResponseVO> handleCallback(@Valid @RequestBody CallbackDTO dto) {
        return Result.success(workOrderService.handleCallback(dto));
    }

    @GetMapping(value = "/callback/page", produces = MediaType.TEXT_HTML_VALUE)
    public String callbackPage(@RequestParam String token) {
        return "<!DOCTYPE html>\n"
                + "<html lang=\"zh-CN\">\n"
                + "<head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1.0\">\n"
                + "<title>工单回调处理</title>\n"
                + "<style>body{font-family:system-ui;max-width:600px;margin:50px auto;padding:20px}\n"
                + ".card{border:1px solid #e0e0e0;border-radius:8px;padding:24px;margin-bottom:16px}\n"
                + ".btn{display:inline-block;padding:10px 24px;border:none;border-radius:6px;font-size:14px;cursor:pointer;margin:4px}\n"
                + ".btn-confirm{background:#10b981;color:#fff} .btn-ignore{background:#6b7280;color:#fff}\n"
                + ".btn-info{background:#3b82f6;color:#fff} .msg{padding:12px;border-radius:6px;margin:12px 0}\n"
                + ".msg-ok{background:#d1fae5;color:#065f46} .msg-err{background:#fee2e2;color:#991b1b}\n"
                + "textarea{width:100%;box-sizing:border-box;padding:8px;border:1px solid #d1d5db;border-radius:6px;margin:8px 0}\n"
                + "</style></head>\n"
                + "<body>\n"
                + "<h2>工单回调处理</h2>\n"
                + "<div class=\"card\" id=\"form-area\">\n"
                + "  <p><strong>Token:</strong> <span id=\"token-display\"></span></p>\n"
                + "  <p><strong>操作备注：</strong></p>\n"
                + "  <textarea id=\"comment\" rows=\"3\" placeholder=\"输入备注信息（驳回时必填，不少于10字）\"></textarea>\n"
                + "  <div style=\"margin-top:16px\">\n"
                + "    <button class=\"btn btn-confirm\" onclick=\"submit('CONFIRM')\">确认</button>\n"
                + "    <button class=\"btn btn-ignore\" onclick=\"submit('IGNORE')\">忽略</button>\n"
                + "    <button class=\"btn btn-info\" onclick=\"submit('MORE_INFO')\">需要更多信息</button>\n"
                + "  </div>\n"
                + "</div>\n"
                + "<div id=\"result\" style=\"display:none\"></div>\n"
                + "<script>\n"
                + "const token='" + token + "';\n"
                + "document.getElementById('token-display').textContent=token.substring(0,8)+'...';\n"
                + "async function submit(action){\n"
                + "  const comment=document.getElementById('comment').value;\n"
                + "  try{\n"
                + "    const r=await fetch('/workorder/callback',{method:'POST',\n"
                + "      headers:{'Content-Type':'application/json'},\n"
                + "      body:JSON.stringify({token,action,comment})});\n"
                + "    const j=await r.json();\n"
                + "    const el=document.getElementById('result');\n"
                + "    el.style.display='block';\n"
                + "    if(j.code===200){\n"
                + "      el.innerHTML='<div class=\"msg msg-ok\">操作成功！工单状态已变更为: '+j.data.newStatus+'</div>';\n"
                + "      document.getElementById('form-area').style.display='none';\n"
                + "    }else{\n"
                + "      el.innerHTML='<div class=\"msg msg-err\">错误: '+j.message+'</div>';\n"
                + "    }\n"
                + "  }catch(e){\n"
                + "    document.getElementById('result').style.display='block';\n"
                + "    document.getElementById('result').innerHTML='<div class=\"msg msg-err\">请求失败: '+e.message+'</div>';\n"
                + "  }\n"
                + "}\n"
                + "</script></body></html>";
    }
}
