package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService userService;
    /**
     * 查询是否关注
     * @param followUserId
     * @return
     */
    @Override
    public Result isFollow(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        QueryWrapper<Follow> queryWrapper=new QueryWrapper<>();
        queryWrapper.eq("user_id",userId).eq("follow_user_id",followUserId);
        int count = this.count(queryWrapper);
        return Result.ok(count>0);
    }

    /**
     * 关注与取关
     * @param followUserId
     * @param isFollow
     * @return
     */
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        //获取用户ID
        Long userId = UserHolder.getUser().getId();
        String key="follows:"+userId;
        //判断是否关注
        if(isFollow){
            //关注，新增数据
            Follow follow= new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);
            if(isSuccess){
                stringRedisTemplate.opsForSet().add(key,followUserId.toString());
            }
        }else {
            //取关 删除
            QueryWrapper<Follow> queryWrapper=new QueryWrapper<>();
            queryWrapper.eq("user_id",userId).eq("follow_user_id",followUserId);
            boolean isSuccess = this.remove(queryWrapper);
            if(isSuccess){
                stringRedisTemplate.opsForSet().remove(key,followUserId.toString());
            }
        }
        return Result.ok();
    }

    /**
     * 共同关注
     * @param id
     * @return
     */
    @Override
    public Result followCommons(Long id) {
        //获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        String key="follows:"+userId;
        String key2="follows:"+id;
        //求当前用户与目标用户的交集
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key, key2);
        //解析Id
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        //查询用户
        List<UserDTO> users = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(users);
    }
}
