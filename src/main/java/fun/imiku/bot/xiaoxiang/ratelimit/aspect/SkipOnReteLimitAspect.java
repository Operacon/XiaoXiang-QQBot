package fun.imiku.bot.xiaoxiang.ratelimit.aspect;


import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class SkipOnReteLimitAspect {
    @Around("@annotation(fun.imiku.bot.xiaoxiang.ratelimit.annotation.SkipOnRateLimit)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        // TODO
        return null;
    }
}
