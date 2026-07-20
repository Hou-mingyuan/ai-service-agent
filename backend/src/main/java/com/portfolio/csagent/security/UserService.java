package com.portfolio.csagent.security;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.portfolio.csagent.common.BizException;
import com.portfolio.csagent.config.AppProperties;
import com.portfolio.csagent.entity.AppUser;
import com.portfolio.csagent.mapper.AppUserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    private final AppUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;

    public UserService(AppUserMapper userMapper, PasswordEncoder passwordEncoder, AppProperties properties) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    public AuthenticatedUser authenticate(String username, String password) {
        AppUser user = userMapper.selectOne(Wrappers.<AppUser>lambdaQuery()
                .eq(AppUser::getTenantId, properties.getDemo().getTenantId())
                .eq(AppUser::getUsername, username)
                .last("limit 1"));
        if (user == null || user.getEnabled() == null || user.getEnabled() != 1
                || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BizException(401, "用户名或密码错误");
        }
        Role role = Role.fromId(user.getRole());
        if (role == null) {
            throw new BizException(403, "账号角色配置无效");
        }
        return new AuthenticatedUser(user.getId(), user.getTenantId(), user.getUsername(),
                user.getDisplayName(), role);
    }
}
