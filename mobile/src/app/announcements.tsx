import { useState } from "react";
import { Image, Linking, Text, View } from "react-native";
import * as DocumentPicker from "expo-document-picker";
import { useSession } from "../lib/session";
import { useAppearance } from "../lib/appearance";
import { academicApi, announcementApi, fileApi } from "../api/services";
import { Btn, Card, Chip, Empty, Field, QueryState, Screen, Title, friendlyMessage, useAsync } from "../ui/kit";
import { fmtDate } from "../lib/format";
import { mediaUrl } from "../lib/media";

const AUDIENCE = ["ALL", "TEACHERS", "PARENTS", "STUDENTS"];

type NoticeAttachment = { id?: string; fileName?: string; fileUrl: string; contentType?: string };

function isImageAttachment(att: NoticeAttachment) {
  const ct = (att.contentType || "").toLowerCase();
  const target = `${att.fileName || ""} ${att.fileUrl || ""}`.toLowerCase();
  return ct.startsWith("image/") || /\.(jpe?g|png|webp|gif)(\?|$|\s)/i.test(target);
}

export default function Announcements() {
  const { user } = useSession();
  const { colors } = useAppearance();
  const list = useAsync(() => announcementApi.list());
  const classes = useAsync(() => academicApi.classes(), [], user?.role === "SCHOOL_ADMIN" || user?.role === "PRINCIPAL");
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [audience, setAudience] = useState("ALL");
  const [classId, setClassId] = useState("");
  const [sectionId, setSectionId] = useState("");
  const [attachments, setAttachments] = useState<NoticeAttachment[]>([]);
  const [uploading, setUploading] = useState(false);
  const [err, setErr] = useState("");
  const sections = useAsync(() => (classId ? academicApi.sections(classId) : Promise.resolve([])), [classId], Boolean(classId));
  const canPost = user?.role === "SCHOOL_ADMIN" || user?.role === "PRINCIPAL";

  const pickFiles = async () => {
    setErr("");
    try {
      const picked = await DocumentPicker.getDocumentAsync({
        type: ["image/*", "video/*", "application/pdf", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"],
        multiple: true,
        copyToCacheDirectory: true,
      });
      if (picked.canceled || !picked.assets?.length) return;
      setUploading(true);
      const uploaded: NoticeAttachment[] = [];
      for (const asset of picked.assets) {
        const up = await fileApi.upload({
          uri: asset.uri,
          name: asset.name || "attachment",
          type: asset.mimeType || "application/octet-stream",
        });
        if (up?.url) {
          uploaded.push({
            id: up.id,
            fileName: up.fileName || asset.name || "Attachment",
            fileUrl: up.url,
            contentType: up.contentType || asset.mimeType,
          });
        }
      }
      if (uploaded.length) {
        setAttachments((prev) => [...prev, ...uploaded]);
      }
    } catch (e) {
      setErr(friendlyMessage(e));
    } finally {
      setUploading(false);
    }
  };

  return (
    <Screen colors={colors} onRefresh={() => void list.reload()} refreshing={list.loading}>
      <Title title="Announcements" subtitle="Published notices for this school" colors={colors} />
      {canPost ? (
        <Card colors={colors}>
          <Field label="Title" colors={colors} value={title} onChangeText={setTitle} />
          <Field label="Body" colors={colors} value={body} onChangeText={setBody} multiline />
          <Text style={{ color: colors.muted, fontSize: 12, marginBottom: 6 }}>Audience</Text>
          <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 8 }}>
            {AUDIENCE.map((a) => (
              <Chip key={a} label={a} selected={audience === a} onPress={() => setAudience(a)} colors={colors} />
            ))}
          </View>
          <Text style={{ color: colors.muted, fontSize: 12, marginBottom: 6 }}>Optional class</Text>
          <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 8 }}>
            <Chip label="Whole school" selected={!classId} onPress={() => { setClassId(""); setSectionId(""); }} colors={colors} />
            {(classes.data || []).map((c) => (
              <Chip key={c.id} label={c.name} selected={classId === c.id} onPress={() => { setClassId(c.id); setSectionId(""); }} colors={colors} />
            ))}
          </View>
          {classId ? (
            <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 8, marginBottom: 8 }}>
              {(sections.data || []).map((s) => (
                <Chip key={s.id} label={s.name} selected={sectionId === s.id} onPress={() => setSectionId(s.id)} colors={colors} />
              ))}
            </View>
          ) : null}
          <View style={{ marginBottom: 8 }}>
            <Btn label={uploading ? "Uploading…" : "Attach files"} ghost colors={colors} disabled={uploading} onPress={() => void pickFiles()} />
            {attachments.map((att, idx) => (
              <View key={att.fileUrl + idx} style={{ flexDirection: "row", alignItems: "center", justifyContent: "space-between", marginTop: 6 }}>
                <Text style={{ color: colors.ink, flex: 1 }} numberOfLines={1}>{att.fileName || "Attachment"}</Text>
                <Btn label="Remove" ghost colors={colors} onPress={() => setAttachments((prev) => prev.filter((x) => x.fileUrl !== att.fileUrl))} />
              </View>
            ))}
          </View>
          {err ? <Text style={{ color: colors.bad, marginBottom: 8 }}>{err}</Text> : null}
          <Btn
            label="Publish"
            colors={colors}
            disabled={!title.trim() || !body.trim() || uploading}
            onPress={async () => {
              setErr("");
              try {
                await announcementApi.create({
                  title,
                  body,
                  audience,
                  classId: classId || undefined,
                  sectionId: sectionId || undefined,
                  ...(attachments[0]?.fileUrl ? { attachmentUrl: attachments[0].fileUrl } : {}),
                  attachments,
                });
                setTitle("");
                setBody("");
                setAttachments([]);
                await list.reload();
              } catch (e) {
                setErr(friendlyMessage(e));
              }
            }}
          />
        </Card>
      ) : null}
      <QueryState loading={list.loading} error={list.error} data={list.data} onRetry={() => void list.reload()} colors={colors} empty={<Empty title="No announcements" hint="Nothing has been published yet." colors={colors} />}>
        {(list.data || []).map((a) => {
          const items: NoticeAttachment[] = Array.isArray(a.attachments) && a.attachments.length
            ? a.attachments
            : a.attachmentUrl
              ? [{ fileUrl: a.attachmentUrl, fileName: "Attachment" }]
              : [];
          return (
            <Card key={a.id} colors={colors}>
              <Text style={{ color: colors.ink, fontWeight: "800" }}>{a.title}</Text>
              <Text style={{ color: colors.muted }}>{fmtDate(a.publishDate || a.createdAt)} · {a.audience || "ALL"}</Text>
              {a.body ? <Text style={{ color: colors.ink, marginTop: 6 }}>{a.body}</Text> : null}
              {items.length > 0 ? (
                <View style={{ marginTop: 10, gap: 8 }}>
                  {items.map((att, idx) => {
                    const resolved = mediaUrl(att.fileUrl);
                    if (!resolved) return null;
                    return (
                      <View key={resolved + idx} style={{ gap: 6 }}>
                        {isImageAttachment(att) ? (
                          <Image
                            source={{ uri: resolved }}
                            style={{ width: "100%", height: 180, borderRadius: 10, backgroundColor: colors.line }}
                            resizeMode="cover"
                          />
                        ) : null}
                        <Btn
                          label={att.fileName || "Open attachment"}
                          ghost
                          colors={colors}
                          onPress={() => void Linking.openURL(resolved)}
                        />
                      </View>
                    );
                  })}
                </View>
              ) : null}
            </Card>
          );
        })}
      </QueryState>
    </Screen>
  );
}
