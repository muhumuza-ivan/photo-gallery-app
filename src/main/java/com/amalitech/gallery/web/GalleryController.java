package com.amalitech.gallery.web;

import com.amalitech.gallery.config.AppProperties;
import com.amalitech.gallery.domain.Photo;
import com.amalitech.gallery.domain.PhotoRepository;
import com.amalitech.gallery.storage.ImageStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class GalleryController {

    private static final Logger log = LoggerFactory.getLogger(GalleryController.class);

    private final PhotoRepository photos;
    private final ImageStore images;
    private final AppProperties props;

    public GalleryController(PhotoRepository photos, ImageStore images, AppProperties props) {
        this.photos = photos;
        this.images = images;
        this.props = props;
    }

    @GetMapping("/")
    public String gallery(Model model) {
        List<Photo> all = photos.findAllByOrderByCreatedAtDesc();
        model.addAttribute("photos", all);
        model.addAttribute("cdn", "https://" + props.cloudfrontDomain());
        model.addAttribute("maxUploadMb", props.maxUploadMb());
        return "index";
    }

    @PostMapping("/photos")
    public String upload(@RequestParam("file") MultipartFile file,
                         @RequestParam("description") String description,
                         RedirectAttributes redirect) {
        if (file.isEmpty()) {
            redirect.addFlashAttribute("error", "Choose an image before adding it.");
            return "redirect:/";
        }
        String trimmed = description == null ? "" : description.trim();
        if (trimmed.isEmpty()) {
            redirect.addFlashAttribute("error", "Add a description so the photo has a caption.");
            return "redirect:/";
        }
        if (trimmed.length() > 500) {
            trimmed = trimmed.substring(0, 500);
        }

        try {
            ImageStore.StoredImage stored = images.store(file);
            photos.save(new Photo(trimmed, stored.key(), stored.contentType(), stored.sizeBytes()));
            redirect.addFlashAttribute("message", "Photo added.");
        } catch (ImageStore.UnsupportedImageException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("Upload failed", e);
            redirect.addFlashAttribute("error", "The upload did not complete. Try again.");
        }
        return "redirect:/";
    }
}
