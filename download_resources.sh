echo "talkback/src/main/res/drawable-anydpi-v26/icon_tb4d.xml" > tempfile.txt
echo "talkback/src/main/res/drawable-anydpi-v26/icon_tb4d_round.xml" >> tempfile.txt
echo "talkback/src/main/res/drawable-hdpi/icon_tb4d.png" >> tempfile.txt
echo "talkback/src/main/res/drawable-hdpi/icon_tb4d_round.png" >> tempfile.txt
echo "talkback/src/main/res/drawable-ldpi/icon_tb4d.png" >> tempfile.txt
echo "talkback/src/main/res/drawable-ldpi/icon_tb4d_round.png" >> tempfile.txt
echo "talkback/src/main/res/drawable-mdpi/icon_tb4d.png" >> tempfile.txt
echo "talkback/src/main/res/drawable-mdpi/icon_tb4d_round.png" >> tempfile.txt
echo "talkback/src/main/res/drawable-xhdpi/icon_tb4d.png" >> tempfile.txt
echo "talkback/src/main/res/drawable-xhdpi/icon_tb4d_round.png" >> tempfile.txt
echo "talkback/src/main/res/drawable-xxhdpi/icon_tb4d.png" >> tempfile.txt
echo "talkback/src/main/res/drawable-xxhdpi/icon_tb4d_round.png" >> tempfile.txt
echo "talkback/src/main/res/drawable-xxxhdpi/icon_tb4d.png" >> tempfile.txt
echo "talkback/src/main/res/drawable-xxxhdpi/icon_tb4d_round.png" >> tempfile.txt
echo "talkback/src/main/res/drawable/ic_tb4d_launcher_background.xml" >> tempfile.txt
echo "talkback/src/main/res/drawable/ic_tb4d_launcher_foreground.xml" >> tempfile.txt
echo "talkback/src/main/res/drawable/talkback_intro.png" >> tempfile.txt
echo "utils/src/main/res/drawable/rounded_corner.xml" >> tempfile.txt
echo "utils/src/main/res/drawable/rounded_corner_1.xml" >> tempfile.txt
echo "utils/src/main/res/drawable/rounded_corner_2.xml" >> tempfile.txt
echo "utils/src/main/res/drawable/rounded_corner_3.xml" >> tempfile.txt
echo "utils/src/main/res/drawable/rounded_corner_4.xml" >> tempfile.txt
echo "utils/src/main/res/drawable/rounded_corner_5.xml" >> tempfile.txt
echo "utils/src/main/res/drawable/rounded_corner_6.xml" >> tempfile.txt
echo "utils/src/main/res/drawable/rounded_corner_7.xml" >> tempfile.txt
echo "utils/src/main/res/drawable/rounded_corner_8.xml" >> tempfile.txt
echo "utils/src/main/res/drawable/rounded_corner_9.xml" >> tempfile.txt
echo "utils/src/main/res/drawable/rounded_corner_10.xml" >> tempfile.txt
echo "utils/src/main/res/drawable/rounded_corner_11.xml" >> tempfile.txt
echo "utils/src/main/res/drawable/rounded_corner_12.xml" >> tempfile.txt
echo "utils/src/main/res/drawable/rounded_corner_13.xml" >> tempfile.txt
echo "utils/src/main/res/drawable/rounded_corner_14.xml" >> tempfile.txt
echo "utils/src/main/res/drawable/rounded_corner_15.xml" >> tempfile.txt
echo "utils/src/main/res/drawable/rounded_corner_16.xml" >> tempfile.txt
echo "utils/src/main/res/values/integers.xml" >> tempfile.txt
echo "utils/src/main/res/drawable/toast_transition.xml" >> tempfile.txt

URL=https://raw.githubusercontent.com/qbalsdon/talkback/feature/tb4d_modifications/
mkdir ./utils/src/main/res/drawable/
cat tempfile.txt | while read ITEM
do
   curl -L $URL$ITEM -o ./$ITEM
done

rm tempfile.txt
