package com.example.cinema.blImpl.sales;

import com.example.cinema.bl.sales.TicketService;
import com.example.cinema.blImpl.management.hall.HallServiceForBl;
import com.example.cinema.blImpl.management.schedule.ScheduleServiceForBl;
import com.example.cinema.blImpl.promotion.ActivityServiceForBl;
import com.example.cinema.blImpl.promotion.CouponServiceForBl;
import com.example.cinema.blImpl.promotion.VIPServiceForBl;
import com.example.cinema.data.sales.TicketMapper;
import com.example.cinema.po.*;
import com.example.cinema.vo.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;

/**
 * Created by liying on 2019/4/16.
 */
@Service
public class TicketServiceImpl implements TicketService {

    @Autowired
    TicketMapper ticketMapper;
    @Autowired
    ScheduleServiceForBl scheduleService;
    @Autowired
    HallServiceForBl hallService;
    @Autowired
    CouponServiceForBl couponService;
    @Autowired
    ActivityServiceForBl activityService;
    @Autowired
    VIPServiceForBl vipService;

    @Override
    @Transactional
    public ResponseVO addTicket(TicketForm ticketForm) {
        return null;
    }

    @Override
    @Transactional
    public ResponseVO completeTicket(List<Integer> id, int couponId) {
        //TODO:1. 默认成功(√)
        //   2. 校验优惠券是否存在、是否能用(√)
        //   3. 根据活动赠送优惠券(√)

        if (id.size()==0 || id==null){
            return ResponseVO.buildFailure("票不存在");
        }

        Ticket ticket=ticketMapper.selectTicketById(id.get(0));
        ScheduleItem scheduleItem=scheduleService.getScheduleItemById(ticket.getScheduleId());
        if(! isCouponEnable(couponId,scheduleItem.getFare()*id.size(),ticket.getUserId())){
            for (int i:id){
                ticketMapper.updateTicketState(i,0);
            }
            return ResponseVO.buildFailure("优惠券不能使用");
        }else{
            for (int i:id){
                ticketMapper.updateTicketState(i,1);
            }
        }

        //赠送优惠券
        List<Activity> list= (List<Activity>) activityService.getActivitiesByMovie(scheduleItem.getMovieId()).getContent();
        Timestamp timestamp=new Timestamp(System.currentTimeMillis());
        for (Activity temp:list){
            if (timestamp.after(temp.getStartTime()) && timestamp.before(temp.getEndTime())){
                couponService.issueCoupon(temp.getCoupon().getId(),ticket.getUserId());
            }
        }

        return ResponseVO.buildSuccess();
    }

    @Override
    public ResponseVO getBySchedule(int scheduleId) {
        try {
            List<Ticket> tickets = ticketMapper.selectTicketsBySchedule(scheduleId);
            ScheduleItem schedule=scheduleService.getScheduleItemById(scheduleId);
            Hall hall=hallService.getHallById(schedule.getHallId());
            int[][] seats=new int[hall.getRow()][hall.getColumn()];
            tickets.stream().forEach(ticket -> {
                seats[ticket.getRowIndex()][ticket.getColumnIndex()]=1;
            });
            ScheduleWithSeatVO scheduleWithSeatVO=new ScheduleWithSeatVO();
            scheduleWithSeatVO.setScheduleItem(schedule);
            scheduleWithSeatVO.setSeats(seats);
            return ResponseVO.buildSuccess(scheduleWithSeatVO);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseVO.buildFailure("失败");
        }
    }

    @Override
    public ResponseVO getTicketByUser(int userId) {
        return null;
    }

    @Override
    @Transactional
    public ResponseVO completeByVIPCard(List<Integer> id, int couponId) {
        //TODO:1. 调用VIPService的方法更新会员卡余额(√)
        //   2. 校验优惠券是否存在、是否能用(√)
        //   3. 用boolean ResponseVO.success表示支付是否成功(√)
        //   4. 根据活动赠送优惠券(√)

        if (id.size()==0 || id==null){
            return ResponseVO.buildFailure("票不存在");
        }

        Ticket ticket=ticketMapper.selectTicketById(id.get(0));
        ScheduleItem scheduleItem=scheduleService.getScheduleItemById(ticket.getScheduleId());
        double totalPay=scheduleItem.getFare()*id.size();
        if(! isCouponEnable(couponId,totalPay,ticket.getUserId())){
            for (int i:id){
                ticketMapper.updateTicketState(i,0);
            }
            return ResponseVO.buildFailure("优惠券不能使用");
        }else{
            for (int i:id){
                ticketMapper.updateTicketState(i,1);
            }
        }
        totalPay=totalPay-((Coupon)couponService.getCoupon(couponId).getContent()).getDiscountAmount();

        //更新会员卡余额
        if (! vipService.getCardByUserId(ticket.getUserId()).getSuccess()){
            return ResponseVO.buildFailure("会员卡获取失败");
        }else{
            VIPCard vipCard= (VIPCard) vipService.getCardByUserId(ticket.getUserId()).getContent();
            if (vipCard.getBalance()<totalPay){
                return ResponseVO.buildFailure("会员卡余额不足");
            }
            vipService.payByCard(vipCard.getId(),vipCard.getBalance()-totalPay);
        }

        //赠送优惠券
        List<Activity> list= (List<Activity>) activityService.getActivitiesByMovie(scheduleItem.getMovieId()).getContent();
        Timestamp timestamp=new Timestamp(System.currentTimeMillis());
        for (Activity temp:list){
            if (timestamp.after(temp.getStartTime()) && timestamp.before(temp.getEndTime())){
                couponService.issueCoupon(temp.getCoupon().getId(),ticket.getUserId());
            }
        }

        return ResponseVO.buildSuccess();
    }

    @Override
    public ResponseVO cancelTicket(List<Integer> id) {
        return null;
    }



    //检验优惠券是否存在，是否能用(门槛，时间)
    private boolean isCouponEnable(int couponId, double totalPay, int userId){
        Coupon coupon=null;
        //检验是否存在
        List<Coupon> couponList= (List<Coupon>) couponService.getCouponsByUser(userId).getContent();
        boolean isOk=false;
        for (Coupon temp:couponList){
            if (temp.getId()==couponId){
                isOk=true;
                coupon=temp;
                break;
            }
        }
        if (!isOk){
            return false;
        }

        Timestamp timestamp=new Timestamp(System.currentTimeMillis());
        if (!(timestamp.after(coupon.getStartTime()) && timestamp.before(coupon.getEndTime()))){
            //检验时间
            return false;
        }
        if(coupon.getTargetAmount()>totalPay){
            //检验门槛金额
            return false;
        }
        return true;
    }

    //通过id获得电影票详细信息
    private Ticket getTicketById(int ticketId){
        return ticketMapper.selectTicketById(ticketId);
    }
}