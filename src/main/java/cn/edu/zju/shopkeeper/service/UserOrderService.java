package cn.edu.zju.shopkeeper.service;

import cn.edu.zju.shopkeeper.domain.UserOrder;
import cn.edu.zju.shopkeeper.domain.req.UserOrderReq;
import cn.edu.zju.shopkeeper.domain.req.UserReq;
import cn.edu.zju.shopkeeper.domain.res.BaseRes;
import cn.edu.zju.shopkeeper.domain.res.ListRes;
import cn.edu.zju.shopkeeper.domain.res.ObjectRes;
import cn.edu.zju.shopkeeper.domain.vo.UserOrderVO;
import cn.edu.zju.shopkeeper.exception.ShopkeeperException;

/**
 * @author Wang Zejie
 * @version V1.0
 * @date 2018/7/16 上午10:58
 * Description 用户订单服务接口
 */
public interface UserOrderService {
    /**
     * 创建新订单
     *
     * @param req
     * @throws ShopkeeperException
     */
    BaseRes createOrder(UserOrderReq req) throws ShopkeeperException;

    /**
     * 删除订单
     *
     * @param req
     * @return
     * @throws ShopkeeperException
     */
    BaseRes deleteOrder(UserOrderReq req) throws ShopkeeperException;

    /**
     * 订单发货
     *
     * @param req
     * @return
     * @throws ShopkeeperException
     */
    BaseRes updateDelivery(UserOrderReq req) throws ShopkeeperException;

    /**
     * 确认收货
     *
     * @param req
     * @return
     * @throws ShopkeeperException
     */
    BaseRes updateComplete(UserOrderReq req) throws ShopkeeperException;

    /**
     * 获取用户的有效订单列表
     *
     * @param req
     * @return
     * @throws ShopkeeperException
     */
    ListRes<UserOrderVO> queryUserOrderList(UserOrderReq req) throws ShopkeeperException;

    /**
     * 根据主键获取订单详情
     *
     * @param req
     * @return
     * @throws ShopkeeperException
     */
    ObjectRes<UserOrderVO> getUserOrderById(UserOrderReq req) throws ShopkeeperException;

    /**
     * 根据订单的状态获取所有订单
     *
     * @param req
     * @return
     * @throws ShopkeeperException
     */
    ListRes<UserOrderVO> queryAllOrderListByStatus(UserOrderReq req) throws ShopkeeperException;

    /**
     * 更新订单状态（包括发货、收货、取消、删除）
     *
     * @param req
     * @return
     * @throws ShopkeeperException
     */
    BaseRes updateOrder(UserOrderReq req) throws ShopkeeperException;

    /**
     * 取消订单
     *
     * @param req
     * @return
     * @throws ShopkeeperException
     */
    BaseRes updateOrderCancel(UserOrderReq req) throws ShopkeeperException;
}
