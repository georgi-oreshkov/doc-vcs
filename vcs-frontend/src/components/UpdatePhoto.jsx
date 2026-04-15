import { useState } from 'react';
import { Modal, ModalContent, ModalHeader, ModalBody, ModalFooter, Button, Avatar } from "@heroui/react";
import { UploadCloud } from 'lucide-react';
import { useUpdateProfile } from '../hooks/useUser';

export default function UpdatePhoto({ isOpen, onOpenChange, currentPhotoUrl }) {
  const [previewUrl, setPreviewUrl] = useState(null);
  const updateProfile = useUpdateProfile();

  const handleFileChange = (e) => {
    const file = e.target.files[0];
    if (file) {
      setPreviewUrl(URL.createObjectURL(file));
    }
  };

  return (
    <Modal 
      isOpen={isOpen} 
      onOpenChange={onOpenChange} 
      placement="center"
      classNames={{ base: "bg-zinc-950 border border-zinc-800" }}
    >
      <ModalContent>
        {(onClose) => (
          <>
            <ModalHeader className="text-white border-b border-zinc-800">Update Profile Photo</ModalHeader>
            <ModalBody className="flex flex-col items-center py-6">
              
              <Avatar
                // If there is a preview URL (newly selected file), show it. Otherwise, show the current photo from props. If neither exists, show a default avatar.
                src={previewUrl || currentPhotoUrl || "https://i.pravatar.cc/150?u=a042581f4e29026024d"}
                className="w-24 h-24 text-large mb-6"
                isBordered
                color="primary"
              />
              
              {/* Drag & Drop zone */}
              <label className="flex flex-col items-center justify-center w-full h-32 border-2 border-zinc-700 border-dashed rounded-xl cursor-pointer bg-zinc-900/50 hover:bg-zinc-800 hover:border-lime-500 transition-all group">
                <div className="flex flex-col items-center justify-center pt-5 pb-6">
                  <UploadCloud className="w-8 h-8 mb-2 text-zinc-500 group-hover:text-lime-400 transition-colors" />
                  <p className="text-sm text-zinc-400 group-hover:text-zinc-300">
                    <span className="font-semibold text-lime-400">Click to browse</span> or drag and drop
                  </p>
                </div>
            
                <input type="file" className="hidden" accept="image/*" onChange={handleFileChange} />
              </label>

            </ModalBody>
            <ModalFooter className="border-t border-zinc-800">
              <Button color="danger" variant="light" onPress={onClose}>
                Cancel
              </Button>
              <Button color="primary" isLoading={updateProfile.isPending} onPress={() => {
                if (previewUrl) {
                  updateProfile.mutate({ photo_url: previewUrl }, { onSuccess: onClose });
                } else {
                  onClose();
                }
              }}>
                Save Photo
              </Button>
            </ModalFooter>
          </>
        )}
      </ModalContent>
    </Modal>
  );
}