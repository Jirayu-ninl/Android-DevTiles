# Hidden framework calls (DisplaySize) are looked up by name with reflection on framework
# classes, which R8 never touches, so nothing in the app needs keeping for them.
