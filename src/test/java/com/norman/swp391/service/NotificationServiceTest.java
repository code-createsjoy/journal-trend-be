package com.norman.swp391.service;

import com.norman.swp391.entity.*;
import com.norman.swp391.entity.enums.*;
import com.norman.swp391.repository.*;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
public class NotificationServiceTest {

    @Autowired
    private NotificationService notificationService;

    @MockitoBean
    private EmailService emailService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PaperRepository paperRepository;

    @Autowired
    private KeywordRepository keywordRepository;

    @Autowired
    private JournalRepository journalRepository;

    @Autowired
    private AuthorRepository authorRepository;

    @Autowired
    private FollowKeywordRepository followKeywordRepository;

    @Autowired
    private FollowJournalRepository followJournalRepository;

    @Autowired
    private FollowAuthorRepository followAuthorRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PaperKeywordRepository paperKeywordRepository;

    @Autowired
    private PaperAuthorRepository paperAuthorRepository;

    @Test
    public void testNotifyNewPapersForSubscriptions() {
        // 1. Create a user
        User user = userRepository.save(User.builder()
                .email("testnotificationuser@example.com")
                .password("password123")
                .fullName("Test Notified User")
                .role(UserRole.RESEARCHER)
                .status(UserStatus.ACTIVE)
                .enabled(true)
                .verified(true)
                .build());

        // 2. Create Keyword, Journal, Author
        Keyword keyword = keywordRepository.save(Keyword.builder()
                .term("Quantum Computing")
                .domain("Physics")
                .createdAt(LocalDateTime.now())
                .build());

        Journal journal = journalRepository.save(Journal.builder()
                .name("Nature Quantum")
                .active(true)
                .createdAt(LocalDateTime.now())
                .build());

        Author author = authorRepository.save(Author.builder()
                .name("John Quantum")
                .affiliation("MIT")
                .citationCount(42)
                .build());

        // 3. User follows all three
        followKeywordRepository.save(FollowKeyword.builder()
                .user(user)
                .keyword(keyword)
                .followedAt(LocalDateTime.now())
                .build());

        followJournalRepository.save(FollowJournal.builder()
                .user(user)
                .journal(journal)
                .createdAt(LocalDateTime.now())
                .build());

        // 4. Create 3 Papers
        // Paper 1: has the keyword, has the journal (matches both, should only trigger 1 notification for Paper 1)
        Paper paper1 = paperRepository.save(Paper.builder()
                .title("Paper 1 Title: Quantum Computing Breakthrough")
                .abstractText("Abstract of paper 1")
                .doi("10.1000/xyz1")
                .publicationDate(LocalDate.now())
                .citationCount(0)
                .openAccess(true)
                .journalRef(journal)
                .journal(journal.getName())
                .status(PaperStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build());

        paperKeywordRepository.save(PaperKeyword.builder()
                .paper(paper1)
                .keyword(keyword)
                .build());

        // Paper 2: has only the journal
        Paper paper2 = paperRepository.save(Paper.builder()
                .title("Paper 2 Title: In Nature Quantum Journal")
                .abstractText("Abstract of paper 2")
                .doi("10.1000/xyz2")
                .publicationDate(LocalDate.now())
                .citationCount(0)
                .openAccess(true)
                .journalRef(journal)
                .journal(journal.getName())
                .status(PaperStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build());

        // Paper 3: has only the author
        Paper paper3 = paperRepository.save(Paper.builder()
                .title("Paper 3 Title: Written by John Quantum")
                .abstractText("Abstract of paper 3")
                .doi("10.1000/xyz3")
                .publicationDate(LocalDate.now())
                .citationCount(0)
                .openAccess(true)
                .status(PaperStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build());

        // Let the user follow this author too
        followAuthorRepository.save(FollowAuthor.builder()
                .user(user)
                .author(author)
                .followedAt(LocalDateTime.now())
                .build());

        paperAuthorRepository.save(PaperAuthor.builder()
                .paper(paper3)
                .author(author)
                .build());

        // 5. Call notifyNewPapersForSubscriptions
        notificationService.notifyNewPapersForSubscriptions(Set.of(paper1.getId(), paper2.getId(), paper3.getId()));

        // 6. Assert that the notifications are generated correctly
        List<Notification> userNotifications = notificationRepository.findAll().stream()
                .filter(n -> n.getUser().getId().equals(user.getId()))
                .toList();

        // There should be exactly 3 notifications (one for paper1, one for paper2, one for paper3)
        assertEquals(3, userNotifications.size(), "User should have received exactly 3 notifications (one for each matching paper)");

        // Verify that all 3 papers are represented in the notifications
        Set<Long> notifiedPaperIds = new java.util.HashSet<>();
        for (Notification n : userNotifications) {
            notifiedPaperIds.add(n.getPaper().getId());
        }
        assertTrue(notifiedPaperIds.contains(paper1.getId()), "Should contain notification for paper1");
        assertTrue(notifiedPaperIds.contains(paper2.getId()), "Should contain notification for paper2");
        assertTrue(notifiedPaperIds.contains(paper3.getId()), "Should contain notification for paper3");

        // 7. Verify that exactly ONE consolidated email was sent to the user containing all 3 papers
        Mockito.verify(emailService, Mockito.times(1))
                .sendNewPaperNotificationsEmail(
                        Mockito.eq(user.getEmail()),
                        Mockito.eq(user.getFullName()),
                        Mockito.argThat(list -> list != null && list.size() == 3 
                                && list.stream().anyMatch(p -> p.getId().equals(paper1.getId()))
                                && list.stream().anyMatch(p -> p.getId().equals(paper2.getId()))
                                && list.stream().anyMatch(p -> p.getId().equals(paper3.getId())))
                );
    }
}
