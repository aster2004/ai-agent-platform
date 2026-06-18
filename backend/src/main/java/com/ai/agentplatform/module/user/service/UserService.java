package com.ai.agentplatform.module.user.service;

import com.ai.agentplatform.common.exception.BusinessException;
import com.ai.agentplatform.module.user.dto.UserLoginRequest;
import com.ai.agentplatform.module.user.dto.UserRegisterRequest;
import com.ai.agentplatform.module.user.entity.User;
import com.ai.agentplatform.module.user.repository.UserRepository;
import com.ai.agentplatform.module.user.vo.LoginVO;
import com.ai.agentplatform.module.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserVO register(UserRegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("用户名已存在");
        }
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setAvatar(request.getAvatar());
        user.setRole("user");
        user.setStatus("normal");
        user.setPoints(0);
        user.setLevel("v0");
        return UserVO.from(userRepository.save(user));
    }

    public LoginVO login(UserLoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException("用户名或密码错误"));
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        if ("disabled".equals(user.getStatus())) {
            throw new BusinessException("用户已被禁用");
        }
        LoginVO vo = new LoginVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setRole(user.getRole());
        vo.setPoints(user.getPoints());
        vo.setLevel(user.getLevel());
        return vo;
    }

    public UserVO getById(Long id) {
        return UserVO.from(userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("用户不存在")));
    }

    public UserVO getByUsername(String username) {
        return UserVO.from(userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException("用户不存在")));
    }

    public Page<UserVO> list(int page, int size) {
        return userRepository.findAll(PageRequest.of(page, size))
                .map(UserVO::from);
    }

    @Transactional
    public void updateStatus(Long id, String status) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        user.setStatus(status);
        userRepository.save(user);
    }

    @Transactional
    public void delete(Long id) {
        if (!userRepository.existsById(id)) {
            throw new BusinessException("用户不存在");
        }
        userRepository.deleteById(id);
    }

    @Transactional
    public String uploadAvatar(Long userId, MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException("请选择图片文件");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.matches(".*\\.(jpg|jpeg|png|gif)$")) {
            throw new BusinessException("只支持jpg、jpeg、png、gif格式的图片");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        try {
            String uploadDir = "uploads/avatar";
            Path path = Paths.get(uploadDir);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
            String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            String newFilename = UUID.randomUUID().toString() + extension;
            Path filePath = path.resolve(newFilename);
            Files.write(filePath, file.getBytes());
            String avatarUrl = "/api/user/avatar/" + newFilename;
            user.setAvatar(avatarUrl);
            userRepository.save(user);
            return avatarUrl;
        } catch (IOException e) {
            throw new BusinessException("上传失败");
        }
    }

    public byte[] getAvatar(String filename) {
        try {
            Path filePath = Paths.get("uploads/avatar", filename);
            if (Files.exists(filePath)) {
                return Files.readAllBytes(filePath);
            }
            return null;
        } catch (IOException e) {
            return null;
        }
    }
}
