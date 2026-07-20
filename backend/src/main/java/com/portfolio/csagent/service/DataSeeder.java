package com.portfolio.csagent.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import com.portfolio.csagent.entity.Faq;
import com.portfolio.csagent.entity.OrderInfo;
import com.portfolio.csagent.entity.Policy;
import com.portfolio.csagent.entity.Shipment;
import com.portfolio.csagent.entity.Ticket;
import com.portfolio.csagent.entity.AppUser;
import com.portfolio.csagent.entity.KnowledgeDocument;
import com.portfolio.csagent.entity.KnowledgeChunk;
import com.portfolio.csagent.mapper.FaqMapper;
import com.portfolio.csagent.mapper.OrderInfoMapper;
import com.portfolio.csagent.mapper.PolicyMapper;
import com.portfolio.csagent.mapper.ShipmentMapper;
import com.portfolio.csagent.mapper.TicketMapper;
import com.portfolio.csagent.mapper.AppUserMapper;
import com.portfolio.csagent.mapper.KnowledgeDocumentMapper;
import com.portfolio.csagent.mapper.KnowledgeChunkMapper;
import com.portfolio.csagent.config.AppProperties;
import com.portfolio.csagent.security.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 启动时幂等写入示例业务数据，保证「一键起服务 + 示例数据」即可体验。 */
@Component
@Order(1)
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final OrderInfoMapper orderMapper;
    private final ShipmentMapper shipmentMapper;
    private final PolicyMapper policyMapper;
    private final FaqMapper faqMapper;
    private final TicketMapper ticketMapper;
    private final TicketService ticketService;
    private final AppUserMapper userMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;

    public DataSeeder(OrderInfoMapper orderMapper, ShipmentMapper shipmentMapper,
                      PolicyMapper policyMapper, FaqMapper faqMapper, TicketMapper ticketMapper,
                      TicketService ticketService, AppUserMapper userMapper,
                      KnowledgeDocumentMapper documentMapper, KnowledgeChunkMapper chunkMapper,
                      PasswordEncoder passwordEncoder, AppProperties properties) {
        this.orderMapper = orderMapper;
        this.shipmentMapper = shipmentMapper;
        this.policyMapper = policyMapper;
        this.faqMapper = faqMapper;
        this.ticketMapper = ticketMapper;
        this.ticketService = ticketService;
        this.userMapper = userMapper;
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    public void run(String... args) {
        if (!properties.getDemo().isEnabled()) {
            log.info("Demo seed disabled; no sample accounts or business data were created");
            return;
        }
        seedUsers();
        seedOrders();
        seedShipments();
        seedPolicies();
        seedFaqs();
        seedTickets();
        seedKnowledge();
        log.info("Mock 演示数据就绪：账号 {}、订单 {}、物流 {}、保单 {}、知识文档 {}、工单 {}",
                userMapper.selectCount(null),
                orderMapper.selectCount(null), shipmentMapper.selectCount(null),
                policyMapper.selectCount(null), documentMapper.selectCount(null),
                ticketMapper.selectCount(null));
    }

    private void seedUsers() {
        if (userMapper.selectCount(null) > 0) {
            return;
        }
        user("customer", "customer123", "演示客户", Role.CUSTOMER);
        user("agent", "agent123", "坐席小智", Role.AGENT);
        user("supervisor", "super123", "客服主管", Role.SUPERVISOR);
        user("admin", "admin123", "系统管理员", Role.ADMIN);
    }

    private void user(String username, String password, String displayName, Role role) {
        AppUser user = new AppUser();
        user.setTenantId(tenant());
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setDisplayName(displayName);
        user.setRole(role.getId());
        user.setEnabled(1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);
    }

    private void seedOrders() {
        if (orderMapper.selectCount(null) > 0) {
            return;
        }
        order("123", "演示用户A", "无线蓝牙耳机 Pro", "299.00", "SHIPPED", "上海市演示区示例路1号", 2);
        order("1002", "演示用户B", "智能手表 S8", "1299.00", "DELIVERED", "北京市演示区示例路2号", 6);
        order("1003", "演示用户C", "扫地机器人 T20", "1899.00", "PAID", "广州市演示区示例路3号", 1);
        order("1004", "演示用户D", "声波电动牙刷", "199.00", "REFUNDING", "深圳市演示区示例路4号", 4);
    }

    private void order(String no, String customer, String product, String amount, String status,
                       String address, int daysAgo) {
        OrderInfo o = new OrderInfo();
        o.setTenantId(tenant());
        o.setOwnerUsername("customer");
        o.setOrderNo(no);
        o.setCustomer(customer);
        o.setProduct(product);
        o.setAmount(new BigDecimal(amount));
        o.setStatus(status);
        o.setAddress(address);
        o.setAdapterSource("mock");
        o.setCreatedAt(LocalDateTime.now().minusDays(daysAgo));
        orderMapper.insert(o);
    }

    private void seedShipments() {
        if (shipmentMapper.selectCount(null) > 0) {
            return;
        }
        shipment("123", "SF1234567890", "顺丰速运", "IN_TRANSIT", "上海转运中心 已发出");
        shipment("1002", "JD9988776655", "京东物流", "SIGNED", "北京朝阳站 已由本人签收");
        shipment("1003", null, "中通快递", "PENDING", "商家备货中，待揽收");
    }

    private void shipment(String orderNo, String trackingNo, String carrier, String status,
                          String location) {
        Shipment s = new Shipment();
        s.setTenantId(tenant());
        s.setOwnerUsername("customer");
        s.setOrderNo(orderNo);
        s.setTrackingNo(trackingNo);
        s.setCarrier(carrier);
        s.setStatus(status);
        s.setLastLocation(location);
        s.setAdapterSource("mock");
        s.setUpdatedAt(LocalDateTime.now().minusHours(6));
        shipmentMapper.insert(s);
    }

    private void seedPolicies() {
        if (policyMapper.selectCount(null) > 0) {
            return;
        }
        policy("PAI2024001", "演示用户A", "百万医疗险", "386.00", "ACTIVE",
                "2026-01-01", "2027-01-01", "2026-08-01");
        policy("PAI2024002", "演示用户B", "重大疾病险", "5800.00", "ACTIVE",
                "2026-02-15", "2027-02-15", "2026-09-15");
        policy("PAI2024003", "演示用户C", "家用车商业险", "4200.00", "LAPSED",
                "2025-05-20", "2026-05-20", "2026-05-20");
    }

    private void policy(String no, String holder, String product, String premium, String status,
                        String eff, String exp, String next) {
        Policy p = new Policy();
        p.setTenantId(tenant());
        p.setOwnerUsername("customer");
        p.setPolicyNo(no);
        p.setHolder(holder);
        p.setProduct(product);
        p.setPremium(new BigDecimal(premium));
        p.setStatus(status);
        p.setEffectiveDate(eff);
        p.setExpireDate(exp);
        p.setNextPaymentDate(next);
        p.setAdapterSource("mock");
        policyMapper.insert(p);
    }

    private void seedFaqs() {
        if (faqMapper.selectCount(null) > 0) {
            return;
        }
        faq("退货政策是怎样的？", "支持 7 天无理由退货，商品需保持完好、配件与吊牌齐全。生鲜及定制类商品除外。",
                "退货,退款,7天,无理由", "ORDER");
        faq("如何开具发票？", "支持电子普通发票与增值税专用发票，可在「订单详情-申请开票」提交，1-3 个工作日开出。",
                "发票,开票,报销", "ORDER");
        faq("配送需要多久？", "标准快递一般 2-3 个工作日送达，偏远地区 3-5 天；可在「物流查询」查看实时进度。",
                "配送,时效,多久,几天,快递", "LOGISTICS");
        faq("保险理赔流程是什么？", "理赔需提供保单号、身份证明及相关票据，线上提交后 3-5 个工作日审核，通过后 1-2 日到账。",
                "理赔,报案,赔付,保险", "POLICY");
        faq("人工客服的服务时间？", "人工客服在线时间为每日 9:00-21:00；其余时段可留言，智能客服 7×24 小时为您服务。",
                "人工,客服时间,上班,工作时间", "OTHER");
    }

    private void faq(String q, String a, String kw, String category) {
        Faq f = new Faq();
        f.setQuestion(q);
        f.setAnswer(a);
        f.setKeywords(kw);
        f.setCategory(category);
        faqMapper.insert(f);
    }

    private void seedTickets() {
        if (ticketMapper.selectCount(null) > 0) {
            return;
        }
        Ticket t1 = ticketService.create(null, "LOGISTICS", "订单 1003 迟迟未发货",
                "客户反映下单 3 天仍未发货，要求尽快处理。", "HIGH", "AGENT", "演示用户C");
        ticketService.assign(t1.getId(), "Agent-01");

        Ticket t2 = ticketService.create(null, "REFUND", "订单 1004 退款进度咨询",
                "客户询问退款到账时间。", "MEDIUM", "AGENT", "演示用户D");
        ticketService.transitionSystem(t2.getId(), "RESOLVED", "Agent-02", "已告知退款 3-5 个工作日到账");
        ticketService.transitionSystem(t2.getId(), "CLOSED", "Agent-02", "客户确认，关闭工单");

        ticketService.create(null, "COMPLAINT", "对客服态度不满",
                "客户对此前的处理表示不满，情绪较激动。", "URGENT", "AGENT", "演示用户B");
    }

    private void seedKnowledge() {
        if (documentMapper.selectCount(null) > 0) {
            return;
        }
        document("售后退货政策", "kb://demo/returns",
                "普通商品支持签收后 7 天无理由退货。商品、配件、包装和吊牌需保持完好。生鲜、定制和已激活数字商品不适用。退款在仓库验收后 1 至 3 个工作日原路退回。");
        document("发票申请指南", "kb://demo/invoice",
                "电子普通发票可在订单详情中申请。增值税专用发票需要提交完整开票资料并通过审核。发票通常在 1 至 3 个工作日内开具，可在订单详情下载。");
        document("配送时效说明", "kb://demo/shipping",
                "标准快递通常在发货后 2 至 3 个工作日送达，偏远地区预计 3 至 5 个工作日。具体进度以物流查询工具返回的实时轨迹为准。");
        document("保险理赔材料清单", "kb://demo/claims",
                "保险理赔需要保单号、被保险人身份证明、事故或诊疗相关证明以及费用票据。材料提交后通常在 3 至 5 个工作日完成初审，最终结果以人工审核为准。");
        document("人工客服与升级规则", "kb://demo/handoff",
                "人工坐席在线时间为每日 09:00 至 21:00。客户主动要求人工、识别到明显负面情绪或系统无法可靠回答时进入转接队列。紧急投诉按最高优先级处理。");
    }

    private void document(String title, String sourceUri, String content) {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setTenantId(tenant());
        document.setTitle(title);
        document.setSourceUri(sourceUri);
        document.setContent(content);
        document.setChecksum(sha256(content));
        document.setStatus("READY");
        document.setCreatedBy("system-seed");
        document.setCreatedAt(LocalDateTime.now());
        document.setUpdatedAt(LocalDateTime.now());
        documentMapper.insert(document);

        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setTenantId(tenant());
        chunk.setDocumentId(document.getId());
        chunk.setChunkIndex(0);
        chunk.setContent(content);
        chunk.setSearchText((title + " " + content).toLowerCase());
        chunk.setCreatedAt(LocalDateTime.now());
        chunkMapper.insert(chunk);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to checksum demo knowledge", exception);
        }
    }

    private String tenant() {
        return properties.getDemo().getTenantId();
    }
}
