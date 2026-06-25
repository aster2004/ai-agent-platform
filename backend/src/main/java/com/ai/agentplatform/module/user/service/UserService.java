package com.ai.agentplatform.module.user.service;

import com.ai.agentplatform.common.exception.BusinessException;
import com.ai.agentplatform.module.user.dto.UserLoginRequest;
import com.ai.agentplatform.module.user.dto.UserRegisterRequest;
import com.ai.agentplatform.module.user.entity.User;
import com.ai.agentplatform.module.user.entity.UserCheckin;
import com.ai.agentplatform.module.user.entity.UserPointsLog;
import com.ai.agentplatform.module.user.repository.UserCheckinRepository;
import com.ai.agentplatform.module.user.repository.UserPointsLogRepository;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserPointsLogRepository pointsLogRepository;
    private final UserCheckinRepository checkinRepository;

    public static final String POINT_TYPE_REGISTER = "REGISTER";
    public static final String POINT_TYPE_SET_NICKNAME = "SET_NICKNAME";
    public static final String POINT_TYPE_BIND_PHONE = "BIND_PHONE";
    public static final String POINT_TYPE_BIND_EMAIL = "BIND_EMAIL";
    public static final String POINT_TYPE_UPLOAD_AVATAR = "UPLOAD_AVATAR";
    public static final String POINT_TYPE_CHECKIN_DAILY = "CHECKIN_DAILY";
    public static final String POINT_TYPE_CHECKIN_7DAYS = "CHECKIN_7DAYS";
    public static final String POINT_TYPE_CHECKIN_30DAYS = "CHECKIN_30DAYS";

    @Transactional
    public UserVO register(UserRegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("用户名已被注册");
        }
        boolean hasPhone = request.getPhone() != null && !request.getPhone().isEmpty();
        boolean hasEmail = request.getEmail() != null && !request.getEmail().isEmpty();
        if (!hasPhone && !hasEmail) {
            throw new BusinessException("请输入手机号或邮箱");
        }
        if (hasPhone && userRepository.existsByPhone(request.getPhone())) {
            throw new BusinessException("手机号已被注册");
        }
        if (hasEmail && userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("邮箱已被注册");
        }
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setAvatar(request.getAvatar());
        user.setPhone(request.getPhone());
        user.setEmail(request.getEmail());
        user.setRole("user");
        user.setStatus("normal");
        user.setPoints(0);
        user.setLevel("v0");
        User savedUser = userRepository.save(user);
        addPoints(savedUser.getId(), 50, POINT_TYPE_REGISTER, "注册账号");
        return UserVO.from(savedUser);
    }

    public LoginVO login(UserLoginRequest request) {
        String loginId = request.getUsername();
        User user = userRepository.findByUsername(loginId)
                .or(() -> userRepository.findByPhone(loginId))
                .or(() -> userRepository.findByEmail(loginId))
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
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
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

    public Page<UserVO> searchByUsername(String username, int page, int size) {
        return userRepository.findByUsernameContainingIgnoreCase(username, PageRequest.of(page, size))
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
    public UserVO updateProfile(Long id, String nickname, String phone, String email) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        if (nickname != null && !nickname.equals(user.getNickname()) && user.getNickname() == null) {
            user.setNickname(nickname);
            addPoints(id, 20, POINT_TYPE_SET_NICKNAME, "完善昵称");
        } else if (nickname != null) {
            user.setNickname(nickname);
        }
        if (phone != null && !phone.equals(user.getPhone())) {
            if (userRepository.existsByPhone(phone)) {
                throw new BusinessException("手机号已被绑定");
            }
            user.setPhone(phone);
            if (user.getPhone() == null) {
                addPoints(id, 30, POINT_TYPE_BIND_PHONE, "绑定手机号");
            }
        }
        if (email != null && !email.equals(user.getEmail())) {
            if (userRepository.existsByEmail(email)) {
                throw new BusinessException("邮箱已被绑定");
            }
            user.setEmail(email);
            if (user.getEmail() == null) {
                addPoints(id, 30, POINT_TYPE_BIND_EMAIL, "绑定邮箱");
            }
        }
        return UserVO.from(userRepository.save(user));
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
        boolean firstUpload = user.getAvatar() == null;
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
            if (firstUpload) {
                addPoints(userId, 20, POINT_TYPE_UPLOAD_AVATAR, "上传头像");
            }
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

    @Transactional
    public void addPoints(Long userId, int points, String type, String description) {
        if (pointsLogRepository.existsByUserIdAndType(userId, type)) {
            return;
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        user.setPoints(user.getPoints() + points);
        user.setLevel(calculateLevel(user.getPoints()));
        userRepository.save(user);
        UserPointsLog log = new UserPointsLog();
        log.setUserId(userId);
        log.setPoints(points);
        log.setType(type);
        log.setDescription(description);
        pointsLogRepository.save(log);
    }

    private String calculateLevel(int points) {
        if (points >= 25000) return "v6";
        if (points >= 10000) return "v5";
        if (points >= 4000) return "v4";
        if (points >= 1500) return "v3";
        if (points >= 500) return "v2";
        if (points >= 100) return "v1";
        return "v0";
    }

    @Transactional
    public Map<String, Object> checkin(Long userId) {
        LocalDate today = LocalDate.now();
        if (checkinRepository.existsByUserIdAndCheckinDate(userId, today)) {
            throw new BusinessException("今日已签到");
        }
        List<LocalDate> checkinDates = checkinRepository.findCheckinDatesByUserIdOrderByDateDesc(userId);
        int consecutiveDays = 0;
        if (!checkinDates.isEmpty()) {
            LocalDate lastDate = checkinDates.get(0);
            if (lastDate.equals(today)) {
                consecutiveDays = 1;
            } else if (lastDate.plusDays(1).equals(today)) {
                consecutiveDays = 1;
                for (int i = 1; i < checkinDates.size(); i++) {
                    if (checkinDates.get(i).plusDays(1).equals(checkinDates.get(i - 1))) {
                        consecutiveDays++;
                    } else {
                        break;
                    }
                }
            }
        }
        UserCheckin checkin = new UserCheckin();
        checkin.setUserId(userId);
        checkin.setCheckinDate(today);
        checkinRepository.save(checkin);
        addPoints(userId, 5, POINT_TYPE_CHECKIN_DAILY, "每日签到");
        if (consecutiveDays >= 7 && consecutiveDays % 7 == 0) {
            addPoints(userId, 20, POINT_TYPE_CHECKIN_7DAYS, "连续签到7天");
        }
        if (consecutiveDays >= 30 && consecutiveDays % 30 == 0) {
            addPoints(userId, 100, POINT_TYPE_CHECKIN_30DAYS, "连续签到30天");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "签到成功");
        result.put("consecutiveDays", consecutiveDays);
        result.put("todayPoints", 5);
        return result;
    }

    public Map<String, Object> getCheckinStats(Long userId) {
        LocalDate today = LocalDate.now();
        boolean checkedInToday = checkinRepository.existsByUserIdAndCheckinDate(userId, today);
        List<LocalDate> checkinDates = checkinRepository.findCheckinDatesByUserIdOrderByDateDesc(userId);
        int consecutiveDays = 0;
        if (!checkinDates.isEmpty()) {
            LocalDate lastDate = checkinDates.get(0);
            if (lastDate.equals(today)) {
                consecutiveDays = 1;
                for (int i = 1; i < checkinDates.size(); i++) {
                    if (checkinDates.get(i).plusDays(1).equals(checkinDates.get(i - 1))) {
                        consecutiveDays++;
                    } else {
                        break;
                    }
                }
            } else if (lastDate.plusDays(1).equals(today)) {
                consecutiveDays = 1;
                for (int i = 1; i < checkinDates.size(); i++) {
                    if (checkinDates.get(i).plusDays(1).equals(checkinDates.get(i - 1))) {
                        consecutiveDays++;
                    } else {
                        break;
                    }
                }
            }
        }
        long totalCheckins = checkinRepository.countByUserId(userId);
        LocalDate monthStart = today.withDayOfMonth(1);
        long monthCheckins = checkinRepository.countByUserIdAndCheckinDateAfter(userId, monthStart);
        Map<String, Object> result = new HashMap<>();
        result.put("checkedInToday", checkedInToday);
        result.put("consecutiveDays", consecutiveDays);
        result.put("totalCheckins", totalCheckins);
        result.put("monthCheckins", monthCheckins);
        return result;
    }

    @Transactional
    public Map<String, Object> getNewbieTasks(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        List<Map<String, Object>> tasks = new ArrayList<>();
        boolean registerCompleted = true;
        if (registerCompleted) {
            addPoints(userId, 50, POINT_TYPE_REGISTER, "注册账号");
        }
        tasks.add(createTaskItem("注册账号", 50, "新用户注册即送", POINT_TYPE_REGISTER, registerCompleted));
        boolean nicknameCompleted = user.getNickname() != null && !user.getNickname().isEmpty();
        if (nicknameCompleted) {
            addPoints(userId, 20, POINT_TYPE_SET_NICKNAME, "完善昵称");
        }
        tasks.add(createTaskItem("完善昵称", 20, "设置个人昵称", POINT_TYPE_SET_NICKNAME, nicknameCompleted));
        boolean phoneCompleted = user.getPhone() != null && !user.getPhone().isEmpty();
        if (phoneCompleted) {
            addPoints(userId, 30, POINT_TYPE_BIND_PHONE, "绑定手机号");
        }
        tasks.add(createTaskItem("绑定手机号", 30, "绑定手机号", POINT_TYPE_BIND_PHONE, phoneCompleted));
        boolean emailCompleted = user.getEmail() != null && !user.getEmail().isEmpty();
        if (emailCompleted) {
            addPoints(userId, 30, POINT_TYPE_BIND_EMAIL, "绑定邮箱");
        }
        tasks.add(createTaskItem("绑定邮箱", 30, "绑定邮箱", POINT_TYPE_BIND_EMAIL, emailCompleted));
        boolean avatarCompleted = user.getAvatar() != null && !user.getAvatar().isEmpty();
        if (avatarCompleted) {
            addPoints(userId, 20, POINT_TYPE_UPLOAD_AVATAR, "上传头像");
        }
        tasks.add(createTaskItem("上传头像", 20, "设置个人头像", POINT_TYPE_UPLOAD_AVATAR, avatarCompleted));
        int completedCount = (int) tasks.stream().filter(t -> (Boolean) t.get("completed")).count();
        int totalPoints = tasks.stream().mapToInt(t -> (Integer) t.get("points")).sum();
        int earnedPoints = tasks.stream().filter(t -> (Boolean) t.get("completed")).mapToInt(t -> (Integer) t.get("points")).sum();
        Map<String, Object> result = new HashMap<>();
        result.put("tasks", tasks);
        result.put("completedCount", completedCount);
        result.put("totalCount", tasks.size());
        result.put("totalPoints", totalPoints);
        result.put("earnedPoints", earnedPoints);
        return result;
    }

    private Map<String, Object> createTaskItem(String name, int points, String description, String type, boolean completed) {
        Map<String, Object> item = new HashMap<>();
        item.put("name", name);
        item.put("points", points);
        item.put("description", description);
        item.put("type", type);
        item.put("completed", completed);
        return item;
    }
}