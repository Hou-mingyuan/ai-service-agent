package com.portfolio.csagent.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.portfolio.csagent.entity.Faq;
import com.portfolio.csagent.entity.OrderInfo;
import com.portfolio.csagent.entity.Policy;
import com.portfolio.csagent.entity.Shipment;
import com.portfolio.csagent.entity.Ticket;
import com.portfolio.csagent.mapper.FaqMapper;
import com.portfolio.csagent.mapper.OrderInfoMapper;
import com.portfolio.csagent.mapper.PolicyMapper;
import com.portfolio.csagent.mapper.ShipmentMapper;
import com.portfolio.csagent.mapper.TicketMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

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

    public DataSeeder(OrderInfoMapper orderMapper, ShipmentMapper shipmentMapper,
                      PolicyMapper policyMapper, FaqMapper faqMapper, TicketMapper ticketMapper,
                      TicketService ticketService) {
        this.orderMapper = orderMapper;
        this.shipmentMapper = shipmentMapper;
        this.policyMapper = policyMapper;
        this.faqMapper = faqMapper;
        this.ticketMapper = ticketMapper;
        this.ticketService = ticketService;
    }

    @Override
    public void run(String... args) {
        seedOrders();
        seedShipments();
        seedPolicies();
        seedFaqs();
        seedTickets();
        log.info("示例数据就绪：订单 {}、物流 {}、保单 {}、FAQ {}、工单 {}",
                orderMapper.selectCount(null), shipmentMapper.selectCount(null),
                policyMapper.selectCount(null), faqMapper.selectCount(null),
                ticketMapper.selectCount(null));
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
        o.setOrderNo(no);
        o.setCustomer(customer);
        o.setProduct(product);
        o.setAmount(new BigDecimal(amount));
        o.setStatus(status);
        o.setAddress(address);
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
        s.setOrderNo(orderNo);
        s.setTrackingNo(trackingNo);
        s.setCarrier(carrier);
        s.setStatus(status);
        s.setLastLocation(location);
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
        p.setPolicyNo(no);
        p.setHolder(holder);
        p.setProduct(product);
        p.setPremium(new BigDecimal(premium));
        p.setStatus(status);
        p.setEffectiveDate(eff);
        p.setExpireDate(exp);
        p.setNextPaymentDate(next);
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
        ticketService.transition(t2.getId(), "RESOLVED", "Agent-02", "已告知退款 3-5 个工作日到账");
        ticketService.transition(t2.getId(), "CLOSED", "Agent-02", "客户确认，关闭工单");

        ticketService.create(null, "COMPLAINT", "对客服态度不满",
                "客户对此前的处理表示不满，情绪较激动。", "URGENT", "AGENT", "演示用户B");
    }
}
