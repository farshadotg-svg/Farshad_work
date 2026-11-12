# دفتر هوشمند فرشاد — نسخه اندروید

پوسته اندروید و گردش‌کار ساخت APK آماده است.

## مرحله باقی‌مانده برای انتشار APK نسخه جدید

1. فایل HTML نهایی که از گفتگو دریافت کرده‌اید را در گوشی به نام دقیق `index.html` تغییر نام دهید.
2. مخزن را باز کنید: https://github.com/farshadotg-svg/Farshad_work
3. از **Add file → Upload files** فایل `index.html` را در ریشه مخزن بارگذاری کنید و **Commit changes** را بزنید.
4. بارگذاری `index.html` به‌صورت خودکار ساخت APK را شروع می‌کند: https://github.com/farshadotg-svg/Farshad_work/actions
5. پس از موفقیت اجرا، از بخش **Artifacts** فایل `Farshad-Water-Invoice-APK` را دریافت کنید؛ ZIP را باز کنید و `app-debug.apk` را نصب کنید.

## پشتیبان و بازگشت

نسخه قبلی HTML اندروید بدون تغییر در مسیر زیر نگهداری شده است:

`backups/android/index-farshad-v13-corrected-before-2026-11-13.html`

فایل اصلی قبلی نیز با نام `index-farshad-v13-corrected.html` حفظ شده است. ساخت APK جدید از `index.html` انجام می‌شود و دیگر فایل v13 را جایگزین آن نمی‌کند.

## مشخصات ساخت

- Android application ID: `ir.farshad.waterinvoice`
- نسخه ساخت جدید: `1.2` (versionCode 3)
- ساخت با همان کلید امضای CI قبلی برای امکان نصب به‌روزرسانی روی نسخه قبلی.

## نکته

برنامه از Firebase و ورود گوگل استفاده می‌کند. ورود گوگل در WebView باید پس از نصب APK روی گوشی آزمایش شود؛ ساخت موفق APK به‌تنهایی عملکرد ورود گوگل را تضمین نمی‌کند.
