
## 1. Tổng quan
`CustomZoomImageView` là một Custom View kế thừa từ `AppCompatImageView`, cung cấp các tính năng nâng cao sau cho việc hiển thị ảnh trong Android:
*   **Zoom In/Out**: Sử dụng cử chỉ 2 ngón tay (Pinch) để phóng to/thu nhỏ.
*   **Drag (Kéo thả)**: Di chuyển ảnh khi đang ở chế độ zoom.
*   **Fling (Quán tính)**: Hỗ trợ trượt ảnh mượt mà khi người dùng vuốt nhanh (momentum dragging).
*   **Double Tap**: Nhấn đúp để phóng to nhanh hoặc thu nhỏ về mặc định.
*   **Outside Tap**: Bắt sự kiện khi người dùng nhấn vào vùng trống (background) bên ngoài ảnh. Tự động thu nhỏ ảnh về ban đầu khi sự kiện xảy ra.
*   **Custom Sizing Modes**: Hỗ trợ set kích thước hiển thị của **nội dung ảnh** (không phải kích thước View) theo nhiều kịch bản phức tạp (Fixed pixel, phần trăm màn hình, tỷ lệ khung hình...).

---

## 2. Hướng dẫn sử dụng

### 2.1. Thêm vào XML Layout
Sử dụng `CustomZoomImageView` thay thế cho `ImageView` thông thường. Nên để kích thước là `match_parent` để View có không gian rộng rãi cho việc zoom/drag.

```xml
<com.example.myphotoview.CustomZoomImageView
    android:id="@+id/iv_photo"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:src="@drawable/your_image"
    android:background="@android:color/black" />
```

### 2.2. Set ảnh từ code
Hỗ trợ tất cả các phương thức set ảnh chuẩn của `ImageView`. Khi gọi các hàm này, View sẽ tự động tính toán lại matrix để hiển thị đúng kích thước mong muốn.

```kotlin
val photoView = findViewById<CustomZoomImageView>(R.id.iv_photo)

// Load từ Resource
photoView.setImageResource(R.drawable.my_image)

// Load từ Bitmap
photoView.setImageBitmap(myBitmap)

// Load từ thư viện (ví dụ Glide)
Glide.with(this).load(url).into(photoView)
```

### 2.3. Tính năng Outside Tap
Bắt sự kiện khi người dùng tap vào vùng đen (khoảng trống) xung quanh ảnh.

```kotlin
photoView.onOutsidePhotoTapListener = {
    // Logic xử lý, ví dụ: đóng màn hình xem ảnh
    Toast.makeText(this, "Tapped Outside!", Toast.LENGTH_SHORT).show()
    // finish()
}
```

### 2.4. Tính năng Set Kích Thước (Sizing Modes)
View cung cấp một bộ API mạnh mẽ để điều khiển kích thước hiển thị của **ảnh** bên trong View container.

**Lưu ý:** Các hàm này thay đổi độ to nhỏ của *hình ảnh được vẽ ra*, View container vẫn giữ nguyên kích thước (thường là full màn hình). Ảnh sẽ luôn được canh giữa (Center).

#### a. Set theo Pixel cố định
```kotlin
// Set chiều rộng ảnh là 200px, chiều cao tự tính theo tỷ lệ ảnh gốc
photoView.setFixedWidth(200)

// Set chiều cao ảnh là 500px, chiều rộng tự tính
photoView.setFixedHeight(500)

// Set cả 2 chiều cố định (Có thể làm méo ảnh hoặc fit center trong box này tùy logic)
photoView.setFixedSize(300, 300)
```

#### b. Set theo Phần trăm màn hình (%)
Tham số `percent` chạy từ `0.0` đến `1.0` (ví dụ `0.8` là 80%).

```kotlin
// Ảnh rộng bằng 80% bề ngang màn hình
photoView.setWidthPercent(0.8f)

// Ảnh cao bằng 50% chiều cao màn hình
photoView.setHeightPercent(0.5f)

// Ảnh rộng 80% màn hình, chiều cao tính theo tỷ lệ khung hình 16:9
photoView.setWidthPercentageAndAspectRatio(0.8f, 9f / 16f)
```

---

## 3. Giải thích Logic Chi tiết

Lõi của `CustomZoomImageView` xoay quanh việc thao tác với `Matrix` của Android Graphics.

### 3.1. Matrix Architecture
Chúng ta sử dụng mô hình 3 lớp Matrix:
1.  **`baseMatrix`**: Matrix nền tảng. Chịu trách nhiệm đưa ảnh gốc (Drawable) về kích thước hiển thị ban đầu (Initial State) dựa trên `SizingMode` (ví dụ: fit center, fill screen, hay fixed size).
2.  **`suppMatrix` (Supplementary Matrix)**: Matrix bổ sung. Lưu trữ các biến đổi do người dùng thao tác: Zoom (Scale), Drag (Translate).
3.  **`drawMatrix`**: Kết quả cuối cùng. `drawMatrix = baseMatrix * suppMatrix`. Đây là matrix thực tế được set vào `ImageView.imageMatrix` để vẽ lên màn hình.

### 3.2. Logic Zoom & Drag
*   **ScaleGestureDetector**: Dùng để bắt sự kiện 2 ngón tay. Khi phát hiện sự kiện `onScale`, ta lấy `scaleFactor` nhân vào `suppMatrix`.
*   **GestureDetector (onScroll)**: Dùng để bắt sự kiện kéo tay 1 ngón. Khi kéo, ta dịch chuyển `suppMatrix` (`postTranslate`).
*   **Bounds Check (`checkMatrixBounds`)**: Sau mỗi lần thay đổi matrix, hàm này được gọi để đảm bảo ảnh không bị kéo ra quá xa khỏi màn hình. Nếu ảnh nhỏ hơn màn hình, nó sẽ tự động dùng matrix để đưa ảnh về chính giữa (`center`).

### 3.3. Logic Sizing Modes (Tính năng nâng cao)
Đây là phần custom chính so với thư viện gốc. Thay vì chỉ dùng `ScaleType.FIT_CENTER` mặc định, ta tự tính toán `scale` khởi tạo trong hàm `updateBaseMatrix()`.

**Quy trình:**
1.  Lấy kích thước View (`viewWidth`, `viewHeight`) và kích thước ảnh gốc (`drawableWidth`, `drawableHeight`).
2.  Lấy thông tin màn hình (`DisplayMetrics`) để phục vụ tính toán phần trăm.
3.  Dựa vào `sizingMode` đang set, tính toán ra tỷ lệ `scale` cần thiết:
    *   *Ví dụ Case `FIXED_WIDTH_AUTO_HEIGHT`*: `scale = targetWidth / drawableWidth`.
    *   *Ví dụ Case `PERCENT_WIDTH_ASPECT_RATIO`*:
        *   Tính `targetWidthPixel = screenWidth * percentage`.
        *   `scale = targetWidthPixel / drawableWidth`. (Chiều cao tự động đi theo scale này nên tỷ lệ ảnh luôn đúng).
4.  Áp dụng `scale` vào `baseMatrix`.
5.  Dịch chuyển `baseMatrix` để ảnh nằm giữa View container.

### 3.4. Logic Outside Tap
Sử dụng `GestureDetector.onSingleTapConfirmed`:
1.  Lấy hình chữ nhật bao quanh ảnh hiện tại (`getDisplayRect`).
2.  Kiểm tra tọa độ điểm chạm (`e.x`, `e.y`) có nằm trong hình chữ nhật đó không.
3.  Nếu **KHÔNG** nằm trong -> Người dùng tap vào vùng đen -> Gọi callback listener và reset `suppMatrix` về defalt (thu nhỏ ảnh).

---

## 4. Lưu ý khi phát triển tiếp
*   Luôn gọi `updateBaseMatrix()` khi source ảnh thay đổi (`setImageDrawable`...) để đảm bảo logic sizing được áp dụng lại.
*   Cần cẩn thận với trường hợp `width` hoặc `height` của View bằng 0 (lúc chưa layout xong), code đã có check Guard Clause để tránh crash.