document.addEventListener('DOMContentLoaded', () => {
    // 0. Mobile Hamburger Menu Toggle
    const menuToggle = document.querySelector('.menu-toggle');
    const navMenu = document.querySelector('.nav-menu');
    const navLinksList = document.querySelectorAll('.nav-link, .download-nav-btn');

    if (menuToggle && navMenu) {
        menuToggle.addEventListener('click', (e) => {
            e.stopPropagation();
            menuToggle.classList.toggle('active');
            navMenu.classList.toggle('active');
            
            if (navMenu.classList.contains('active')) {
                document.body.style.overflow = 'hidden';
            } else {
                document.body.style.overflow = '';
            }
        });

        // Close menu when a link is clicked
        navLinksList.forEach(link => {
            link.addEventListener('click', () => {
                menuToggle.classList.remove('active');
                navMenu.classList.remove('active');
                document.body.style.overflow = '';
            });
        });

        // Close menu when clicking outside of the nav menu
        document.addEventListener('click', (e) => {
            if (navMenu.classList.contains('active') && !navMenu.contains(e.target) && e.target !== menuToggle) {
                menuToggle.classList.remove('active');
                navMenu.classList.remove('active');
                document.body.style.overflow = '';
            }
        });
    }

    // 1. Send Files to TV Interactive Guide Steps Data
    const stepsData = {
        1: {
            icon: '📥',
            title: 'Step 1: Install Send Files to TV',
            description: 'Go to the Google Play Store on your Android TV or Google TV and install the free "Send Files to TV" (SFTTV) app. Also, install the same app on your Android Mobile Phone from the Play Store.'
        },
        2: {
            icon: '💾',
            title: 'Step 2: Download the APK on Phone',
            description: 'Download the CloudStream TV APK directly onto your Android phone by clicking the "Download APK" button on this website.'
        },
        3: {
            icon: '⚡',
            title: 'Step 3: Transfer via SFTTV',
            description: 'Open the "Send Files to TV" app on both your phone and your TV. On your TV, tap "Receive". On your phone, tap "Send", select the downloaded CloudStream TV APK, and choose your TV from the device list.'
        },
        4: {
            icon: '🚀',
            title: 'Step 4: Install APK on TV',
            description: 'On your Android TV, open any File Manager (like File Commander or CX File Explorer). Navigate to the "Download" folder, click on the received CloudStream TV APK, and select "Install"!'
        }
    };

    const stepButtons = document.querySelectorAll('.guide-step-btn');
    const visualIcon = document.getElementById('visual-icon');
    const visualTitle = document.getElementById('visual-title');
    const visualDescription = document.getElementById('visual-description');

    // Handle interactive step switching
    stepButtons.forEach(button => {
        button.addEventListener('click', () => {
            // Remove active class from all buttons
            stepButtons.forEach(btn => btn.classList.remove('active'));
            
            // Add active class to clicked button
            button.classList.add('active');

            // Retrieve step ID
            const stepId = button.getAttribute('data-step');
            const data = stepsData[stepId];

            if (data) {
                // Apply a quick transition animation to the visual container
                const container = document.querySelector('.visual-container');
                container.style.opacity = '0';
                container.style.transform = 'scale(0.95)';
                
                setTimeout(() => {
                    visualIcon.textContent = data.icon;
                    visualTitle.textContent = data.title;
                    visualDescription.textContent = data.description;
                    
                    container.style.opacity = '1';
                    container.style.transform = 'scale(1)';
                }, 150);
            }
        });
    });

    // 2. Active Navigation link highlighted on Scroll
    const sections = document.querySelectorAll('section, header');
    const navLinks = document.querySelectorAll('nav a');

    window.addEventListener('scroll', () => {
        let currentSectionId = '';
        
        sections.forEach(section => {
            const sectionTop = section.offsetTop;
            const sectionHeight = section.clientHeight;
            if (window.scrollY >= (sectionTop - 120)) {
                currentSectionId = section.getAttribute('id') || '';
            }
        });

        navLinks.forEach(link => {
            link.classList.remove('active');
            if (link.getAttribute('href') === `#${currentSectionId}`) {
                link.classList.add('active');
            }
        });
    });
});
