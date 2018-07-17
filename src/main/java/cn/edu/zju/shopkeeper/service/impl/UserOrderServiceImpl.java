package cn.edu.zju.shopkeeper.service.impl;

import cn.edu.zju.shopkeeper.constants.ShopkeeperConstant;
import cn.edu.zju.shopkeeper.domain.Bankcard;
import cn.edu.zju.shopkeeper.domain.Commodity;
import cn.edu.zju.shopkeeper.domain.OrderCommodityRelationship;
import cn.edu.zju.shopkeeper.domain.UserOrder;
import cn.edu.zju.shopkeeper.domain.req.OrderCommodityRelationshipReq;
import cn.edu.zju.shopkeeper.domain.req.UserOrderReq;
import cn.edu.zju.shopkeeper.domain.res.BaseRes;
import cn.edu.zju.shopkeeper.domain.res.ListRes;
import cn.edu.zju.shopkeeper.domain.res.ObjectRes;
import cn.edu.zju.shopkeeper.domain.vo.CommodityVO;
import cn.edu.zju.shopkeeper.domain.vo.UserOrderVO;
import cn.edu.zju.shopkeeper.enums.ResultEnum;
import cn.edu.zju.shopkeeper.exception.ShopkeeperException;
import cn.edu.zju.shopkeeper.mapper.BankcardMapper;
import cn.edu.zju.shopkeeper.mapper.CommodityMapper;
import cn.edu.zju.shopkeeper.mapper.OrderCommodityRelationshipMapper;
import cn.edu.zju.shopkeeper.mapper.UserOrderMapper;
import cn.edu.zju.shopkeeper.service.UserOrderService;
import cn.edu.zju.shopkeeper.utils.DozerBeanUtil;
import cn.edu.zju.shopkeeper.utils.OrderNumberUtil;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author Wang Zejie
 * @version V1.0
 * @date 2018/7/16 上午10:58
 * Description 用户订单服务实现类
 */
@Service
public class UserOrderServiceImpl implements UserOrderService {
    /**
     * 日志
     */
    private Logger logger = LoggerFactory.getLogger(UserOrderServiceImpl.class);
    private BankcardMapper bankcardMapper;
    private UserOrderMapper userOrderMapper;
    private OrderCommodityRelationshipMapper orderCommodityRelationshipMapper;
    private CommodityMapper commodityMapper;

    @Autowired
    public UserOrderServiceImpl(BankcardMapper bankcardMapper, UserOrderMapper userOrderMapper, OrderCommodityRelationshipMapper orderCommodityRelationshipMapper, CommodityMapper commodityMapper) {
        this.bankcardMapper = bankcardMapper;
        this.userOrderMapper = userOrderMapper;
        this.orderCommodityRelationshipMapper = orderCommodityRelationshipMapper;
        this.commodityMapper = commodityMapper;
    }

    /**
     * 创建新订单
     *
     * @param req
     * @throws ShopkeeperException
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseRes createOrder(UserOrderReq req) throws ShopkeeperException {
        logger.info("invoke UserOrderServiceImpl createOrder, req:{}", req);
        //参数校验
        if (req.getUserId() == null || req.getType() == null ||
                req.getBankcardId() == null || CollectionUtils.isEmpty(req.getCommodityList()) ||
                (ShopkeeperConstant.NEED_DELIVERY.equals(req.getType()) && req.getAddressId() == null)) {
            logger.error("UserOrderServiceImpl createOrder missing param, req:{}", req);
            throw new ShopkeeperException(ResultEnum.MISSING_PARAM);
        }
        BaseRes res = new BaseRes();
        //首先校验商品库存，校验通过后获取商品的列表，便于接下来的库存扣除
        List<Commodity> commodities = checkInventory(req.getCommodityList());
        if (commodities == null) {
            throw new ShopkeeperException(ResultEnum.INVENTORY_SHORTAGE);
        }
        //计算商品总额、总数
        Double totalPrice = 0.0;
        Integer totalNum = 0;
        for (int i = 0; i < commodities.size(); i++) {
            totalPrice += commodities.get(i).getPrice() * req.getCommodityList().get(i).getCount();
            totalNum += req.getCommodityList().get(i).getCount();
        }
        req.setTotalNum(totalNum);
        req.setTotalPrice(totalPrice);
        //创建订单前先进行付款
        if (!pay(req)) {
            throw new ShopkeeperException(ResultEnum.INSUFFICIENT_BALANCE);
        }
        //创建一个新订单，并获取订单的主键
        Integer userOrderId = createNewOrder(req);
        //创建订单-商品关系
        req.setId(userOrderId);
        createOrderCommodityRelationship(req, commodities);
        return res;
    }

    /**
     * 删除订单
     *
     * @param req
     * @return
     * @throws ShopkeeperException
     */
    @Override
    public BaseRes deleteOrder(UserOrderReq req) throws ShopkeeperException {
        logger.info("invoke UserOrderServiceImpl deleteOrder, req:{}", req);
        //参数校验
        if (req.getId() == null) {
            logger.error("UserOrderServiceImpl deleteOrder missing param, req:{}", req);
            throw new ShopkeeperException(ResultEnum.MISSING_PARAM);
        }
        BaseRes res = new BaseRes();
        try {
            userOrderMapper.deleteOrder(req.getId());
            res.setResultCode(ResultEnum.SUCCESS.getCode());
            res.setResultMsg(ResultEnum.SUCCESS.getMsg());
        } catch (Exception e) {
            logger.error("UserOrderServiceImpl deleteOrder error:{}", ExceptionUtils.getStackTrace(e));
            throw new ShopkeeperException(ResultEnum.DATA_UPDATE_FAIL);
        }
        return res;
    }

    /**
     * 订单发货
     *
     * @param req
     * @return
     * @throws ShopkeeperException
     */
    @Override
    public BaseRes updateDelivery(UserOrderReq req) throws ShopkeeperException {
        logger.info("invoke UserOrderServiceImpl updateDelivery, req:{}", req);
        //参数校验
        if (req.getId() == null) {
            logger.error("UserOrderServiceImpl updateDelivery missing param, req:{}", req);
            throw new ShopkeeperException(ResultEnum.MISSING_PARAM);
        }
        BaseRes res = new BaseRes();
        Date date = new Date();
        try {
            UserOrder entity = new UserOrder();
            entity.setId(req.getId());
            entity.setDeliveryTime(date);
            entity.setStatus(ShopkeeperConstant.DELIVERY);
            userOrderMapper.updateDelivery(entity);
            res.setResultCode(ResultEnum.SUCCESS.getCode());
            res.setResultMsg(ResultEnum.SUCCESS.getMsg());
        } catch (Exception e) {
            logger.error("UserOrderServiceImpl updateDelivery error:{}", ExceptionUtils.getStackTrace(e));
            throw new ShopkeeperException(ResultEnum.DATA_UPDATE_FAIL);
        }
        return res;
    }

    /**
     * 确认收货
     *
     * @param req
     * @return
     * @throws ShopkeeperException
     */
    @Override
    public BaseRes updateComplete(UserOrderReq req) throws ShopkeeperException {
        logger.info("invoke UserOrderServiceImpl updateComplete, req:{}", req);
        //参数校验
        if (req.getId() == null) {
            logger.error("UserOrderServiceImpl updateComplete missing param, req:{}", req);
            throw new ShopkeeperException(ResultEnum.MISSING_PARAM);
        }
        BaseRes res = new BaseRes();
        Date date = new Date();
        try {
            UserOrder entity = new UserOrder();
            entity.setId(req.getId());
            entity.setCompleteTime(date);
            entity.setStatus(ShopkeeperConstant.COMPLETED);
            userOrderMapper.updateComplete(entity);
            res.setResultCode(ResultEnum.SUCCESS.getCode());
            res.setResultMsg(ResultEnum.SUCCESS.getMsg());
        } catch (Exception e) {
            logger.error("UserOrderServiceImpl updateComplete error:{}", ExceptionUtils.getStackTrace(e));
            throw new ShopkeeperException(ResultEnum.DATA_UPDATE_FAIL);
        }
        return res;
    }

    /**
     * 获取用户的有效订单列表
     *
     * @param req
     * @return
     * @throws ShopkeeperException
     */
    @Override
    public ListRes<UserOrderVO> queryUserOrderList(UserOrderReq req) throws ShopkeeperException {
        logger.info("invoke UserOrderServiceImpl queryUserOrderList, req:{}", req);
        //参数校验
        if (req.getUserId() == null) {
            logger.error("UserOrderServiceImpl queryUserOrderList missing param, req:{}", req);
            throw new ShopkeeperException(ResultEnum.MISSING_PARAM);
        }
        ListRes<UserOrderVO> res = new ListRes<>();
        try {
            List<UserOrder> list = userOrderMapper.queryUserOrderList(req.getUserId());
            res.setResultList(DozerBeanUtil.mapList(list, UserOrderVO.class));
            res.setResultCode(ResultEnum.SUCCESS.getCode());
            res.setResultMsg(ResultEnum.SUCCESS.getMsg());
        } catch (Exception e) {
            logger.error("UserOrderServiceImpl queryUserOrderList error:{}", ExceptionUtils.getStackTrace(e));
            throw new ShopkeeperException(ResultEnum.DATA_QUERY_FAIL);
        }
        return res;
    }

    /**
     * 根据主键获取订单详情
     *
     * @param req
     * @return
     * @throws ShopkeeperException
     */
    @Override
    public ObjectRes<UserOrderVO> getUserOrderById(UserOrderReq req) throws ShopkeeperException {
        logger.info("invoke UserOrderServiceImpl getUserOrderById, req:{}", req);
        //参数校验
        if (req.getId() == null) {
            logger.error("UserOrderServiceImpl getUserOrderById missing param, req:{}", req);
            throw new ShopkeeperException(ResultEnum.MISSING_PARAM);
        }
        ObjectRes<UserOrderVO> res = new ObjectRes<>();
        try {
            UserOrder userOrder = userOrderMapper.getUserOrderById(req.getId());
            UserOrderVO userOrderVO = DozerBeanUtil.map(userOrder, UserOrderVO.class);
            List<OrderCommodityRelationship> list = orderCommodityRelationshipMapper.queryOrderCommodityRelationshipList(req.getId());
            List<CommodityVO> commodityVOS = new ArrayList<>();
            //查询商品详情，并加入购买数量
            for (OrderCommodityRelationship o : list) {
                Commodity commodity = commodityMapper.getCommodity(o.getCommodityId());
                CommodityVO commodityVO = DozerBeanUtil.map(commodity, CommodityVO.class);
                commodityVO.setCount(o.getCount());
                commodityVOS.add(commodityVO);
            }
            userOrderVO.setCommodityList(commodityVOS);
            res.setResultObj(userOrderVO);
        } catch (Exception e) {
            logger.error("UserOrderServiceImpl getUserOrderById error:{}", ExceptionUtils.getStackTrace(e));
            throw new ShopkeeperException(ResultEnum.DATA_QUERY_FAIL);
        }
        return res;
    }

    /**
     * 校验库存
     *
     * @param list
     * @return
     * @throws ShopkeeperException
     */
    private List<Commodity> checkInventory(List<OrderCommodityRelationshipReq> list) throws ShopkeeperException {
        List<Commodity> commodities = new ArrayList<>();
        try {
            for (OrderCommodityRelationshipReq o : list) {
                Commodity commodity = commodityMapper.getCommodity(o.getCommodityId());
                if (commodity.getInventory() < o.getCount()) {
                    return null;
                }
                commodities.add(commodity);
            }
        } catch (Exception e) {
            logger.error("UserOrderServiceImpl checkInventory error:{}", ExceptionUtils.getStackTrace(e));
            throw new ShopkeeperException(ResultEnum.DATA_QUERY_FAIL);
        }
        return commodities;
    }

    /**
     * 支付
     *
     * @param req
     * @return
     * @throws ShopkeeperException
     */
    private boolean pay(UserOrderReq req) throws ShopkeeperException {
        Date date = new Date();
        try {
            Bankcard bankcard = bankcardMapper.getBankcardById(req.getBankcardId());
            //余额不足，则直接返回
            if (bankcard == null || bankcard.getBalance() < req.getTotalPrice()) {
                return false;
            } else {
                //扣款
                Bankcard entity = new Bankcard();
                entity.setId(req.getBankcardId());
                entity.setBalance(bankcard.getBalance() - req.getTotalPrice());
                entity.setModifyTime(date);
                bankcardMapper.updateBalance(entity);
                return true;
            }
        } catch (Exception e) {
            logger.error("UserOrderServiceImpl pay error:{}", ExceptionUtils.getStackTrace(e));
            throw new ShopkeeperException(ResultEnum.PAY_FAIL);
        }
    }

    /**
     * 创建新订单
     *
     * @param req
     * @return 生成的订单主键
     * @throws ShopkeeperException
     */
    private Integer createNewOrder(UserOrderReq req) throws ShopkeeperException {
        Date date = new Date();
        UserOrder entity = DozerBeanUtil.map(req, UserOrder.class);
        entity.setOrderNumber(OrderNumberUtil.getRandomFileName());
        entity.setCreateTime(date);
        entity.setPayTime(date);
        entity.setState(ShopkeeperConstant.VALID);
        if (ShopkeeperConstant.NOT_NEED_DELIVERY.equals(req.getType())) {
            //无需发货，则订单状态为已完成
            entity.setCompleteTime(date);
            entity.setStatus(ShopkeeperConstant.COMPLETED);
        } else {
            //需要发货，订单状态为待发货
            entity.setStatus(ShopkeeperConstant.WAITING_DELIVERY);
        }
        try {
            userOrderMapper.createOrder(entity);
        } catch (Exception e) {
            logger.error("UserOrderServiceImpl createNewOrder error:{}", ExceptionUtils.getStackTrace(e));
            throw new ShopkeeperException(ResultEnum.DATA_INSERT_FAIL);
        }
        return entity.getId();
    }

    /**
     * 创建订单-商品关联关系
     *
     * @param req
     * @throws ShopkeeperException
     */
    private void createOrderCommodityRelationship(UserOrderReq req, List<Commodity> commodities) throws ShopkeeperException {
        List<OrderCommodityRelationship> list = DozerBeanUtil.mapList(req.getCommodityList(), OrderCommodityRelationship.class);
        Integer userOrderId = req.getId();
        try {
            for (OrderCommodityRelationship o : list) {

                o.setOrderId(userOrderId);
            }
            for (int i = 0; i < list.size(); i++) {
                Integer inventory = commodities.get(i).getInventory() - list.get(i).getCount();
                list.get(i).setOrderId(userOrderId);
                //创建订单-商品关联
                orderCommodityRelationshipMapper.createOrderCommodityRelationship(list.get(i));
                //更新商品库存
                commodities.get(i).setInventory(inventory);
                commodityMapper.updateInventory(commodities.get(i));
            }
        } catch (Exception e) {
            logger.error("UserOrderServiceImpl createOrderCommodityRelationship error:{}", ExceptionUtils.getStackTrace(e));
            throw new ShopkeeperException(ResultEnum.DATA_INSERT_FAIL);
        }

    }
}
