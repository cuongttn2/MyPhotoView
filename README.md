# CustomZoomImageView - Tài liệu hướng dẫn sử dụng và Giải thích Logic

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

## 3. Giải thích Logic Chi tiết và Các Biến Quan Trọng

### 3.1. Các Biến Matrix (Ma trận biến đổi)
Logic cốt lõi của việc Zoom/Move ảnh dựa trên việc nhân các Matrix với nhau. Class này sử dụng mô hình 3 lớp Matrix:

1.  **`baseMatrix`**: 
    *   **Vai trò**: Matrix nền tảng, quyết định trạng thái "ban đầu" (initial state) của ảnh khi chưa zoom/kéo.
    *   **Chức năng**: Đưa ảnh gốc (Drawable) về đúng kích thước và vị trí mong muốn (theo `SizingMode`).
    *   **Ví dụ**: Khi gọi `setFixedWidth(200)`, `baseMatrix` sẽ được set scale sao cho ảnh rộng đúng 200px và translate để ảnh nằm giữa màn hình.
    
2.  **`suppMatrix` (Supplementary Matrix)**: 
    *   **Vai trò**: Matrix bổ sung, lưu trữ các biến đổi do người dùng thao tác.
    *   **Chức năng**: Khi bạn zoom vào 2x, `suppMatrix` sẽ lưu giá trị scale = 2. Khi bạn kéo ảnh sang phải 50px, `suppMatrix` lưu translate x = 50.
    *   **Khi reset**: Gọi `resetScale()` thực chất là reset `suppMatrix` về defalt, giữ nguyên `baseMatrix`.

3.  **`drawMatrix`**:
    *   **Vai trò**: Matrix kết quả cuối cùng để vẽ.
    *   **Công thức**: `drawMatrix = baseMatrix * suppMatrix`.
    *   `imageMatrix` của `ImageView` sẽ được set bằng `drawMatrix`.

### 3.2. Các Biến Logic Khác
*   **`displayRect`**: Hình chữ nhật (RectF) chứa tọa độ thực tế của ảnh đang hiển thị trên màn hình. Nó được tính bằng cách lấy kích thước ảnh gốc nhân với `drawMatrix`. Chúng ta dùng biến này để kiểm tra xem điểm chạm (tap) có nằm trong ảnh hay không (Outside Tap).
*   **`matrixValues`**: Mảng float tạm thời dùng để trích xuất giá trị từ Matrix (như scale hiện tại, vị trí X/Y). Dùng để tránh cấp phát bộ nhớ liên tục trong `onDraw`.
*   **`SizingMode`**: Enum xác định chế độ scale ảnh hiện tại (ví dụ `PERCENT_WIDTH_AUTO_HEIGHT`).
*   **`targetWidthFixed`/`targetHeightFixed`**: Các biến lưu giá trị pixel mục tiêu mà người dùng set.

### 3.3. Giải thích Hàm Quan Trọng

#### `updateBaseMatrix(d: Drawable?)`
Đây là hàm quan trọng nhất để xử lý kích thước ảnh.
*   **Kích hoạt**: Gọi khi ảnh thay đổi (setImage...), khi View thay đổi kích thước (onLayout), hoặc khi người dùng gọi các hàm set kích thước (setSize...).
*   **Logic**:
    1.  Lấy kích thước View và Drawable.
    2.  Dựa vào `sizingMode`, tính toán `scale` cần thiết. Ví dụ `offset` = Width mong muốn / Width ảnh gốc.
    3.  Lưu `scale` đó vào `baseMatrix`.
    4.  Tính toán `translate` để đưa ảnh đã scale về giữa View (Center).
    5.  Reset `suppMatrix` (nếu đang zoom dở thì sẽ bị reset khi đổi kích thước base).
    
#### `checkMatrixBounds()`
Hàm bảo vệ "ranh giới" ảnh.
*   **Kích hoạt**: Sau mỗi lần người dùng Zoom hoặc Drag.
*   **Logic**:
    *   Nếu ảnh nhỏ hơn View container: Buộc ảnh phải nằm giữa centered.
    *   Nếu ảnh lớn hơn View (đang zoom): Đảm bảo người dùng không kéo "quá đà" để lộ khoảng đen (trừ khi chạm mép).
    
#### `onScale(detector)`
Callback từ `ScaleGestureDetector`.
*   Nhận `scaleFactor` (tỷ lệ zoom tương đối, ví dụ 1.05 là to lên 5%).
*   Nhân `scaleFactor` vào `suppMatrix`.
*   Gọi `updateImageMatrix()` để áp dụng lên View.

#### `onScroll(...)`
Callback từ `GestureDetector`.
*   Nhận `distanceX`, `distanceY`.
*   Dịch chuyển `suppMatrix` ngược lại (`-distance`).
*   Kiểm tra nếu đang ở mép ảnh và đang zoom, thì có thể `requestDisallowInterceptTouchEvent` để ViewPager bên ngoài (nếu có) không cướp sự kiện vuốt.

#### `FlingRunnable` (Inner Class)
Xử lý quán tính (Newtons' Law) khi vuốt mạnh.
*   Sử dụng `OverScroller` của Android để tính toán tọa độ theo thời gian.
*   `run()` được gọi đệ quy (thông qua `postOnAnimation`) cho đến khi `scroller` dừng lại. Mỗi lần chạy nó sẽ dịch chuyển `suppMatrix` và vẽ lại View.

---

## 4. Lưu ý khi phát triển tiếp
*   Luôn gọi `updateBaseMatrix()` khi source ảnh thay đổi (`setImageDrawable`...) để đảm bảo logic sizing được áp dụng lại.
*   Cần cẩn thận với trường hợp `width` hoặc `height` của View bằng 0 (lúc chưa layout xong, hoặc View.GONE), code đã có check Guard Clause (`if (viewWidth <= 0) return`) để tránh crash chia cho 0.