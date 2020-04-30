package com.battcn.boot.extend.configuration.redis.limit;

import cn.hutool.core.util.StrUtil;
import com.battcn.boot.extend.configuration.redis.RedisKeyGenerator;
import com.fishingtime.framework.common.web.response.Response;
import com.fishingtime.framework.common.web.response.ResultStatus;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.types.Expiration;

import javax.annotation.Resource;
import java.lang.reflect.Method;

import static com.battcn.boot.extend.configuration.commons.ExtendBeanTemplate.*;

/**
 * @author Levin
 * @since 2018/12/24 0024
 */
@Slf4j
@Aspect
@ConditionalOnProperty(prefix = REDIS_LIMIT_INTERCEPTOR, name = ENABLED, havingValue = TRUE, matchIfMissing = true)
public class RedisLimitInterceptor {

    @Resource
    private RedisLimitHelper redisLimitHelper;
    @Resource
    private RedisKeyGenerator redisKeyGenerator;


    @Around("execution(public * *(..)) && @annotation(com.battcn.boot.extend.configuration.redis.limit.RedisLimit)")
    public Object interceptor(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        RedisLimit limitAnnotation = method.getAnnotation(RedisLimit.class);
        final String prefix = limitAnnotation.prefix();
        final String delimiter = limitAnnotation.delimiter();
        final String description = limitAnnotation.description();
        final long count = limitAnnotation.count();
        final String countKey = limitAnnotation.countKey();
        final long limitExpire = limitAnnotation.expire();
        final long seconds = Expiration.from(limitExpire, limitAnnotation.timeUnit()).getExpirationTimeInSeconds();
        String key = redisKeyGenerator.generate(prefix, delimiter, pjp);
        boolean acquire = true;
        try {
            if (StrUtil.isEmpty(countKey)) {
                acquire = this.redisLimitHelper.tryAcquire(key, count, seconds, description);
            } else {
                acquire = this.redisLimitHelper.tryAcquire(key, countKey, seconds, description);
            }

        } catch (Throwable e) {
            log.error("[limit server exception]", e);
        }
        if (acquire) {
            return pjp.proceed();
        } else {
            return Response.fail(ResultStatus.SYSTEM_ERROR, limitAnnotation.message());
        }
    }
}