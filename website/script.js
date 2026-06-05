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

    // 1b. Google Drive Folder Linking Guide Steps Data
    const linkingStepsData = {
        1: {
            icon: '📁',
            title: 'Step 1: Organize Videos & Photos',
            description: 'Create a folder in your Google Drive and upload only the videos (MP4, MKV, WebM, AVI, MOV) and photos (JPG, JPEG, PNG, WebP) you want to view. Note: Other formats (like PDFs, Word docs, zip archives, or audio files) are ignored to maintain a clean TV interface.'
        },
        2: {
            icon: '🔗',
            title: 'Step 2: Enter URL or ID in TV App',
            description: 'Launch CloudStream TV on your Android TV or Google TV. On the welcome onboarding screen, enter either the full Google Drive folder sharing link or just the folder ID directly into the URL input field. You can optionally give this folder a custom nickname.'
        },
        3: {
            icon: '🔐',
            title: 'Step 3: Google Authenticate (Option 1 or Option 2)',
            description: 'If Option 1 reaches Google\'s concurrent user limit, choose Option 2 to authenticate securely. Scan the QR code with your phone/PC to go to our Render authentication website, enter the 6-character code shown on your TV, and sign in to connect your Google Account.'
        },
        4: {
            icon: '➕',
            title: 'Step 4: Add More Folders via Sidebar',
            description: 'Once you are logged in, your home grid will render. To add another folder, expand the TV sidebar using your remote, select "Add Link", paste the new folder URL or ID, and verify. You can link as many video and photo folders as you like!'
        }
    };

    const linkingStepButtons = document.querySelectorAll('.linking-step-btn');
    const linkingVisualIcon = document.getElementById('linking-visual-icon');
    const linkingVisualTitle = document.getElementById('linking-visual-title');
    const linkingVisualDescription = document.getElementById('linking-visual-description');

    // Handle interactive linking step switching
    linkingStepButtons.forEach(button => {
        button.addEventListener('click', () => {
            // Remove active class from all linking buttons
            linkingStepButtons.forEach(btn => btn.classList.remove('active'));
            
            // Add active class to clicked button
            button.classList.add('active');

            // Retrieve step ID
            const stepId = button.getAttribute('data-step');
            const data = linkingStepsData[stepId];

            if (data) {
                // Apply a quick transition animation to the visual container
                const container = document.querySelector('.linking-visual-container');
                container.style.opacity = '0';
                container.style.transform = 'scale(0.95)';
                
                setTimeout(() => {
                    linkingVisualIcon.textContent = data.icon;
                    linkingVisualTitle.textContent = data.title;
                    linkingVisualDescription.textContent = data.description;
                    
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
