```text
Secure_Real-time_Messaging_App/
│
├── Server/                     # Không gian làm việc của Khoa
│   ├── src/
│   │   ├── main/ServerMain.java  # File chạy Server
│   │   ├── network/            # Quản lý Socket đa luồng, Client Handler
│   │   ├── dto/                # Các object ánh xạ từ Database (User, Message)
│   │   ├── dao/                # Các file truy vấn CSDL (UserDAO, MessageDAO)
│   │   ├── bll/                # Xử lý nghiệp vụ (LoginBLL, GroupBLL)
│   │   └── utils/              # Tiện ích: SecurityUtils (RSA), MailUtils
│   └── lib/                    # Chứa thư viện jar: JDBC (MySQL/SQLServer), JavaMail
│
├── Client/                     # Không gian làm việc của Sự
│   ├── src/
│   │   ├── main/ClientMain.java  # File chạy Client GUI
│   │   ├── gui/                # Các class giao diện Java Swing (LoginFrame, ChatPanel)
│   │   ├── network/            # Quản lý Socket kết nối đến Server, luồng đọc/ghi
│   │   ├── controller/         # Xử lý sự kiện từ GUI gọi xuống network
│   │   └── utils/              # Tiện ích: SecurityUtils (AES), ValidateUtils
│   └── assets/                 # Chứa hình ảnh, icon, sticker cho giao diện
│
├── database/                   # Chứa script CSDL
│   └── schema.sql              # File tạo bảng và dữ liệu mẫu
│
├── .gitignore