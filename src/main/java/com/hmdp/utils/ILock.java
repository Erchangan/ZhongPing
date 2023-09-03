package com.hmdp.utils;

public interface ILock {
    //获取锁
    boolean tryLock(Long expireTime);
    //释放锁
    void unLock();

}
