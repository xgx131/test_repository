package cn.blue16.aistudymate.controller;

import cn.blue16.aistudymate.common.api.ApiResponse;
import cn.blue16.aistudymate.dto.attendance.AttendanceCreateRequest;
import cn.blue16.aistudymate.dto.attendance.AttendanceRecordUpdateRequest;
import cn.blue16.aistudymate.dto.attendance.CheckInRequest;
import cn.blue16.aistudymate.entity.Attendance;
import cn.blue16.aistudymate.entity.User;
import cn.blue16.aistudymate.service.AttendanceService;
import cn.blue16.aistudymate.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 考勤活动管理控制器
 * <p>
 * 提供考勤活动的全生命周期管理服务，包括考勤创建、学生签到、状态管理、
 * 数据统计等功能。支持多班级联合考勤、QR码动态生成、实时统计分析等特性。
 * 基于角色的访问控制，为不同角色提供差异化的操作权限。采用RESTful API设计，
 * 提供统一的响应格式和完整的异常处理机制。
 * </p>
 *
 * <h3>主要功能模块：</h3>
 * <ul>
 * <li>考勤活动创建：支持多班级联合考勤和灵活的时间配置</li>
 * <li>QR码签到：动态生成防截图二维码，支持位置和时间验证</li>
 * <li>考勤管理：实时统计、状态修改、活动控制等管理功能</li>
 * <li>数据分析：提供考勤率统计和个人/班级维度分析</li>
 * <li>权限控制：基于角色的精细化权限管理</li>
 * </ul>
 *
 * <h3>权限控制：</h3>
 * <ul>
 * <li>管理员：拥有所有考勤活动的完整管理权限</li>
 * <li>辅导员：可管理指定班级的考勤活动和记录</li>
 * <li>学生干部：可为本班级创建考勤并协助管理</li>
 * <li>学生：只能参与签到，查看自己的考勤记录</li>
 * </ul>
 *
 * <h3>技术特性：</h3>
 * <ul>
 * <li>多班级联合考勤：一次创建包含多个班级的考勤活动</li>
 * <li>动态QR码：定时刷新二维码，防止截图作弊</li>
 * <li>实时统计：自动计算考勤率和各状态人数</li>
 * <li>请假联动：自动识别已批准的请假申请</li>
 * </ul>
 *
 * @author AI Study Mate Team
 * @version 1.0
 * @since 2024-01-01
 */
@RestController
@RequestMapping("/api/attendances")
public class AttendanceController {

    /**
     * 考勤服务层
     */
    @Autowired
    private AttendanceService attendanceService;

    /**
     * 创建考勤活动
     * <p>
     * 创建新的考勤活动，支持多班级联合签到、课程信息配置、签到时长设置、
     * 地点限制、日期和课时段配置等参数。系统会自动生成唯一的考勤ID和QR码，
     * 并初始化所有相关班级学生的考勤状态。支持与请假系统联动，自动标记请假学生。
     * </p>
     *
     * <h3>权限控制：</h3>
     * <ul>
     * <li>管理员：可为所有班级发起考勤</li>
     * <li>辅导员：仅可为自己管理的班级发起考勤</li>
     * <li>学生干部：仅可为本班级发起考勤</li>
     * </ul>
     *
     * <h3>输入参数：</h3>
     * <ul>
     * <li>classIds: 班级ID列表（必填，支持多班级联合考勤）</li>
     * <li>courseInfo.courseName: 课程名称（必填）</li>
     * <li>courseInfo.location: 签到地点（必填）</li>
     * <li>courseInfo.periods: 课时段列表（必填，1-14的整数数组）</li>
     * <li>checkInDuration: 签到时长（必填，秒数，1-86400）</li>
     * <li>checkInDate: 签到日期（必填，YYYY-MM-DD格式）</li>
     * </ul>
     *
     * <h3>返回结果：</h3>
     * <ul>
     * <li>attendanceId: 考勤ID（单个考勤记录包含所有班级的学生）</li>
     * <li>classIds: 班级ID列表（数组格式）</li>
     * <li>code: 二维码内容（用于学生签到）</li>
     * </ul>
     * 
     * @param request 考勤创建请求，包含所有必要的考勤配置参数
     * @param authentication 当前用户的JWT认证信息
     * @param httpRequest HTTP请求对象，用于获取用户ID和角色
     * @return 考勤创建结果，包含考勤ID、班级列表和二维码
     * @throws BusinessException 当权限不足、参数验证失败或业务逻辑异常时
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'COUNSELOR', 'STUDENT_LEADER')")
    public ApiResponse<Map<String, Object>> createAttendance(@Valid @RequestBody AttendanceCreateRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        try {
            // 从JWT认证信息中获取用户ID和角色
            String currentUserId = (String) httpRequest.getAttribute("currentUserId");
            String currentUserRole = (String) httpRequest.getAttribute("currentUserRole");

            // 输入参数验证
            if (request.getClassIds() == null || request.getClassIds().isEmpty()) {
                return ApiResponse.error("班级ID列表不能为空");
            }

            if (request.getCourseInfo() == null) {
                return ApiResponse.error("课程信息不能为空");
            }

            if (request.getCourseInfo().getPeriods() == null || request.getCourseInfo().getPeriods().isEmpty()) {
                return ApiResponse.error("课时段不能为空");
            }

            // 验证课时段范围
            for (Integer period : request.getCourseInfo().getPeriods()) {
                if (period < 1 || period > 14) {
                    return ApiResponse.error("课时段必须在1-14之间");
                }
            }

            // 创建多班级考勤（现在返回单个考勤记录，包含所有班级的学生）
            Attendance attendance = attendanceService.createMultiClassAttendance(request, currentUserId);

            // 构建返回结果
            Map<String, Object> result = new HashMap<>();
            // 返回单个考勤ID（包含所有班级的学生）
            result.put("attendanceId", attendance.getAttendanceId());
            // 返回班级ID列表（数组格式）
            result.put("classIds", attendance.getClassIds());
            // 返回二维码
            result.put("code", attendance.getQrCode().getCode());

            return ApiResponse.success("考勤创建成功", result);
        } catch (BusinessException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error("创建考勤失败: " + e.getMessage());
        }
    }

    /**
     * 学生签到
     * <p>
     * 处理学生的签到请求，验证QR码的有效性和完整性，检查考勤活动状态，
     * 更新学生的考勤记录。支持位置信息和设备信息的记录。仅允许学生角色访问，
     * 且学生只能为自己所在班级的考勤活动签到。
     * </p>
     *
     * <h3>签到验证：</h3>
     * <ul>
     * <li>QR码有效性验证：检查二维码内容是否匹配</li>
     * <li>过期时间检查：验证二维码是否已过期</li>
     * <li>考勤状态检查：确认考勤活动未结束</li>
     * <li>重复签到检查：防止学生多次签到</li>
     * <li>班级权限验证：确认学生属于考勤班级</li>
     * </ul>
     * 
     * @param attendanceId 考勤活动的唯一标识符
     * @param request 签到请求，包含QR码数据、位置、设备信息等
     * @param authentication 当前用户的JWT认证信息
     * @param httpRequest HTTP请求对象，用于获取用户ID
     * @return 签到结果，包含签到时间和状态信息
     * @throws BusinessException 当QR码无效、已过期、重复签到或权限不足时
     */
    @PostMapping("/{attendanceId}/checkin")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<Map<String, Object>> checkIn(@PathVariable String attendanceId,
            @RequestBody CheckInRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        try {
            // 从JWT认证信息中获取用户ID
            String currentUserId = (String) httpRequest.getAttribute("currentUserId");

            attendanceService.checkIn(attendanceId, request, currentUserId);

            Map<String, Object> result = new HashMap<>();
            result.put("checkInTime", java.time.LocalDateTime.now());

            return ApiResponse.success("签到成功", result);
        } catch (BusinessException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取考勤活动详情与实时统计
     * <p>
     * 获取指定考勤活动的详细信息，包括所有学生的签到状态、实时统计数据、
     * 考勤配置信息等。系统会自动检查考勤是否过期，并自动关闭过期的考勤活动。
     * 支持基于角色的访问控制，不同角色可查看不同组度的信息。
     * </p>
     *
     * <h3>权限控制：</h3>
     * <ul>
     * <li>管理员：可查看所有考勤活动的完整详情</li>
     * <li>辅导员和学生干部：仅可查看自己管理班级的考勤详情</li>
     * <li>学生：仅可查看本班级考勤活动的基本信息</li>
     * </ul>
     * 
     * @param attendanceId 考勤活动的唯一标识符
     * @param authentication 当前用户的JWT认证信息
     * @param httpRequest HTTP请求对象，用于获取用户ID和角色
     * @return 考勤活动的详细信息，包括所有学生记录和统计数据
     * @throws BusinessException 当考勤不存在或权限不足时
     */
    @GetMapping("/{attendanceId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COUNSELOR', 'STUDENT_LEADER', 'STUDENT')")
    public ApiResponse<Attendance> getAttendance(@PathVariable String attendanceId,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        try {
            // 从JWT认证信息中获取用户信息
            String currentUserId = (String) httpRequest.getAttribute("currentUserId");
            String currentUserRole = (String) httpRequest.getAttribute("currentUserRole");

            Optional<Attendance> attendanceOpt = attendanceService.getAttendanceById(attendanceId);
            if (attendanceOpt.isPresent()) {
                Attendance attendance = attendanceOpt.get();

                // 检查考勤是否过期，如果过期则自动关闭
                if (attendance.getExpiredAt() != null &&
                        LocalDateTime.now().isAfter(attendance.getExpiredAt()) &&
                        "ACTIVE".equals(attendance.getStatus())) {
                    // 考勤已过期，自动关闭
                    attendanceService.closeAttendance(attendanceId);
                    // 重新获取更新后的考勤信息
                    attendance = attendanceService.getAttendanceById(attendanceId).orElse(attendance);
                }

                return ApiResponse.success(attendance);
            } else {
                return ApiResponse.error("考勤不存在");
            }
        } catch (BusinessException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取考勤活动列表
     * <p>
     * 根据查询条件和用户角色获取考勤活动列表。支持按班级、日期范围、
     * 考勤状态等条件进行筛选。不同角色可以查看的考勤范围不同，
     * 系统会自动按照角色权限过滤结果。
     * </p>
     *
     * <h3>权限控制：</h3>
     * <ul>
     * <li>管理员：可查看所有班级的考勤列表</li>
     * <li>辅导员：仅可查看自己管理班级的考勤列表</li>
     * <li>学生干部：仅可查看自己管理班级的考勤列表</li>
     * <li>学生：仅可查看本班级的考勤列表</li>
     * </ul>
     * 
     * @param classId 班级ID（可选），用于筛选指定班级的考勤
     * @param startDate 开始日期（可选），用于日期范围查询
     * @param endDate 结束日期（可选），用于日期范围查询
     * @param status 考勤状态（可选），用于筛选特定状态的考勤
     * @param authentication 当前用户的JWT认证信息
     * @param httpRequest HTTP请求对象，用于获取用户ID和角色
     * @return 符合条件的考勤活动列表
     * @throws BusinessException 当查询参数错误或权限不足时
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'COUNSELOR', 'STUDENT_LEADER', 'STUDENT')")
    public ApiResponse<List<Attendance>> getAttendances(@RequestParam(required = false) String classId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String status,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        try {
            // 从JWT认证信息中获取用户信息
            String currentUserId = (String) httpRequest.getAttribute("currentUserId");
            String currentUserRole = (String) httpRequest.getAttribute("currentUserRole");

            List<Attendance> attendances;
            if (classId != null) {
                attendances = attendanceService.getAttendancesByClassId(classId);
            } else {
                // 根据用户权限获取相应的考勤列表
                attendances = attendanceService.getAttendancesByUserRole(currentUserId, currentUserRole, null);
            }
            return ApiResponse.success(attendances);
        } catch (BusinessException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 结束考勤
     * 权限控制：管理员可结束所有班级的考勤活动，辅导员仅可结束自己管理班级的考勤活动，学生干部仅可结束本班级且由自己发起的考勤活动
     * 
     * @param attendanceId   考勤ID
     * @param authentication JWT认证信息
     * @param httpRequest    HTTP请求对象
     * @return 结束考勤结果
     */
    @PutMapping("/{attendanceId}/close")
    @PreAuthorize("hasAnyRole('ADMIN', 'COUNSELOR', 'STUDENT_LEADER')")
    public ApiResponse<Void> closeAttendance(@PathVariable String attendanceId,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        try {
            // 从JWT认证信息中获取用户ID和角色
            String currentUserId = (String) httpRequest.getAttribute("currentUserId");
            String currentUserRole = (String) httpRequest.getAttribute("currentUserRole");

            attendanceService.closeAttendance(attendanceId);
            return ApiResponse.success("考勤已结束");
        } catch (BusinessException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 修改学生考勤状态
     * 权限控制：管理员可修改所有班级学生的考勤记录，辅导员仅可修改自己管理班级内学生的考勤记录
     * 
     * @param attendanceId   考勤ID
     * @param studentId      学生ID
     * @param status         考勤状态
     * @param reason         状态说明（可选）
     * @param authentication JWT认证信息
     * @param httpRequest    HTTP请求对象
     * @return 修改结果
     */
    @PutMapping("/{attendanceId}/records/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COUNSELOR')")
    public ApiResponse<Void> updateAttendanceRecord(@PathVariable String attendanceId,
            @PathVariable String studentId,
            @RequestBody AttendanceRecordUpdateRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        try {
            // 从JWT认证信息中获取用户信息
            String currentUserId = (String) httpRequest.getAttribute("currentUserId");
            String currentUserRole = (String) httpRequest.getAttribute("currentUserRole");

            attendanceService.updateAttendanceRecord(attendanceId, studentId, request.getStatus(), request.getReason());
            return ApiResponse.success("记录修改成功");
        } catch (BusinessException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取动态二维码
     * 功能描述：为指定考勤活动生成新的动态二维码，防止截图扫码行为
     * 权限控制：管理员、辅导员、学生干部可获取动态二维码
     * 
     * @param attendanceId   考勤ID
     * @param authentication JWT认证信息
     * @param httpRequest    HTTP请求对象
     * @return 包含新code的动态二维码数据
     */
    @GetMapping("/{attendanceId}/qrcode")
    @PreAuthorize("hasAnyRole('ADMIN', 'COUNSELOR', 'STUDENT_LEADER')")
    public ApiResponse<Map<String, Object>> getDynamicQRCode(@PathVariable String attendanceId,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        try {
            // 从JWT认证信息中获取用户信息
            String currentUserId = (String) httpRequest.getAttribute("currentUserId");
            String currentUserRole = (String) httpRequest.getAttribute("currentUserRole");

            // 生成新的动态二维码
            Map<String, Object> qrCodeData = attendanceService.generateDynamicQRCode(attendanceId, currentUserId);

            return ApiResponse.success("动态二维码生成成功", qrCodeData);
        } catch (BusinessException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error("生成动态二维码失败: " + e.getMessage());
        }
    }

    /**
     * 获取综合考勤统计
     * 权限控制：管理员可查看所有考勤的详情和统计数据，辅导员仅可查看自己管理班级的考勤详情和统计数据，学生干部仅可查看本班级的考勤详情和统计数据，学生仅可查看本班级的考勤详情但不能查看其他同学的具体签到状态
     * 
     * @param classId        班级ID（可选）
     * @param studentId      学生ID（可选）
     * @param startDate      开始日期（可选）
     * @param endDate        结束日期（可选）
     * @param authentication JWT认证信息
     * @param httpRequest    HTTP请求对象
     * @return 综合考勤统计数据
     */
    @GetMapping("/statistics")
    @PreAuthorize("hasAnyRole('ADMIN', 'COUNSELOR', 'STUDENT_LEADER', 'STUDENT')")
    public ApiResponse<Map<String, Object>> getAttendanceStatistics(@RequestParam(required = false) String classId,
            @RequestParam(required = false) String studentId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        try {
            // 从JWT认证信息中获取用户信息
            String currentUserId = (String) httpRequest.getAttribute("currentUserId");
            String currentUserRole = (String) httpRequest.getAttribute("currentUserRole");

            Map<String, Object> statistics = attendanceService.getAttendanceStatistics(
                    currentUserId, currentUserRole, classId, studentId);

            return ApiResponse.success(statistics);
        } catch (BusinessException e) {
            return ApiResponse.error(e.getMessage());
        }
    }
}