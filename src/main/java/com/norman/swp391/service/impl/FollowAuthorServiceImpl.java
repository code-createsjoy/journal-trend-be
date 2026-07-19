package com.norman.swp391.service.impl;
 
import com.norman.swp391.config.AppProperties;
import com.norman.swp391.dto.response.author.AuthorResponse;
import com.norman.swp391.entity.Author;
import com.norman.swp391.entity.FollowAuthor;
import com.norman.swp391.entity.User;
import com.norman.swp391.exception.BadRequestException;
import com.norman.swp391.exception.ResourceNotFoundException;
import com.norman.swp391.exception.UnauthorizedException;
import com.norman.swp391.mapper.AuthorMapper;
import com.norman.swp391.repository.AuthorRepository;
import com.norman.swp391.repository.FollowAuthorRepository;
import com.norman.swp391.repository.UserRepository;
import com.norman.swp391.repository.PaperAuthorRepository;
import com.norman.swp391.security.SecurityUtils;
import com.norman.swp391.service.FollowAuthorService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
 
@Service
@RequiredArgsConstructor
public class FollowAuthorServiceImpl implements FollowAuthorService {
 
    private final FollowAuthorRepository followAuthorRepository;
    private final AuthorRepository authorRepository;
    private final UserRepository userRepository;
    private final AppProperties appProperties;
    private final PaperAuthorRepository paperAuthorRepository;
 
    /** Follow 1 author — kiểm tra chưa follow trùng và chưa vượt giới hạn số author được follow. */
    @Override
    @Transactional
    public void follow(Long authorId) {
        Long userId = requireUserId();
        if (followAuthorRepository.existsByUserIdAndAuthorId(userId, authorId)) {
            throw new BadRequestException("Already following this author");
        }
        int max = appProperties.getSync().getMaxFollowAuthorsPerUser();
        if (followAuthorRepository.countByUserId(userId) >= max) {
            throw new BadRequestException("You have reached the maximum of " + max + " followed authors");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        Author author = authorRepository.findById(authorId)
                .orElseThrow(() -> new ResourceNotFoundException("Author", authorId));
        
        followAuthorRepository.save(FollowAuthor.builder()
                .user(user)
                .author(author)
                .followedAt(LocalDateTime.now())
                .build());
    }
 
    /** Unfollow 1 author (không lỗi nếu vốn dĩ chưa follow). */
    @Override
    @Transactional
    public void unfollow(Long authorId) {
        Long userId = requireUserId();
        followAuthorRepository.findByUserIdAndAuthorId(userId, authorId)
                .ifPresent(followAuthorRepository::delete);
    }
 
    /** Danh sách author mà user hiện tại đang follow, kèm số paper + hIndex (ưu tiên giá trị thật). */
    @Override
    @Transactional(readOnly = true)
    public List<AuthorResponse> listFollowed() {
        Long userId = requireUserId();
        return followAuthorRepository.findByUserId(userId).stream()
                .map(FollowAuthor::getAuthor)
                .map(author -> {
                    AuthorResponse response = AuthorMapper.toResponse(author);
                    if (response != null) {
                        int paperCount = (int) paperAuthorRepository.countByAuthorId(author.getId());
                        int citations = author.getCitationCount();
                        response.setPapers(Math.max(paperCount, 1));
                        // Ưu tiên hIndex thật đã enrich từ OpenAlex; chỉ ước lượng khi chưa có (null)
                        response.setHIndex(author.getHIndex() != null ? author.getHIndex() : estimateHIndex(citations));
                    }
                    return response;
                })
                .toList();
    }

    /** Ước lượng h-index từ tổng citation (h sao cho h^2 <= citations) — chỉ dùng khi chưa enrich được hIndex thật. */
    private int estimateHIndex(int citations) {
        int h = 0;
        while ((h + 1) * (h + 1) <= citations) {
            h++;
        }
        return Math.max(h, 1);
    }
 
    /** Lấy userId của user đang đăng nhập, throw nếu chưa xác thực. */
    private Long requireUserId() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new UnauthorizedException("Not authenticated");
        }
        return userId;
    }
}
